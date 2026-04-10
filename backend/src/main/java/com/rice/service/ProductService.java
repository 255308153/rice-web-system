package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.entity.Order;
import com.rice.entity.OrderItem;
import com.rice.entity.Product;
import com.rice.mapper.OrderItemMapper;
import com.rice.mapper.OrderMapper;
import com.rice.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Cacheable(
            cacheNames = "product:list",
            key = "'' + #page + ':' + #size + ':' + (#keyword == null ? '' : #keyword.trim()) + ':' + (#shopId == null ? '' : #shopId)"
    )
    public Page<Product> list(int page, int size, String keyword, Long shopId) {
        Page<Product> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (shopId != null) {
            wrapper.eq(Product::getShopId, shopId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }
        return productMapper.selectPage(pageObj, wrapper);
    }

    @Cacheable(cacheNames = "product:detail", key = "#id")
    public Product getById(Long id) {
        return productMapper.selectById(id);
    }

    public Page<Product> recommend(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 20));

        List<Product> activeProducts = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        Page<Product> result = new Page<>(safePage, safeSize);
        if (activeProducts == null || activeProducts.isEmpty()) {
            result.setRecords(List.of());
            result.setTotal(0);
            return result;
        }

        Map<Long, Product> productMap = activeProducts.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Product::getId, item -> item, (a, b) -> a, LinkedHashMap::new));

        List<Order> paidOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, 1, 2, 3, 4)
                .select(Order::getId, Order::getUserId));
        if (paidOrders == null || paidOrders.isEmpty()) {
            return paginateProducts(buildFallbackRecommendations(activeProducts, Set.of(), Map.of()), safePage, safeSize);
        }

        Map<Long, Long> orderUserMap = paidOrders.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Order::getId, Order::getUserId, (a, b) -> a));
        Set<Long> orderIds = orderUserMap.keySet();
        List<OrderItem> allItems = orderIds.isEmpty()
                ? List.of()
                : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));

        Map<Long, Set<Long>> orderProducts = new HashMap<>();
        Map<Long, Integer> itemOrderFrequency = new HashMap<>();
        Map<Long, Integer> itemSalesWeight = new HashMap<>();
        Map<Long, Integer> userPreferenceWeight = new HashMap<>();

        for (OrderItem item : allItems) {
            if (item.getOrderId() == null || item.getProductId() == null || !productMap.containsKey(item.getProductId())) {
                continue;
            }
            orderProducts.computeIfAbsent(item.getOrderId(), key -> new HashSet<>()).add(item.getProductId());
            itemSalesWeight.merge(item.getProductId(), Math.max(1, item.getQuantity() == null ? 1 : item.getQuantity()), Integer::sum);
            Long ownerId = orderUserMap.get(item.getOrderId());
            if (ownerId != null && ownerId.equals(userId)) {
                userPreferenceWeight.merge(item.getProductId(), Math.max(1, item.getQuantity() == null ? 1 : item.getQuantity()), Integer::sum);
            }
        }

        for (Set<Long> productIds : orderProducts.values()) {
            for (Long productId : productIds) {
                itemOrderFrequency.merge(productId, 1, Integer::sum);
            }
        }

        Set<Long> userPurchased = userPreferenceWeight.keySet();
        if (userPurchased.isEmpty()) {
            return paginateProducts(buildFallbackRecommendations(activeProducts, Set.of(), itemSalesWeight), safePage, safeSize);
        }

        Map<Long, Map<Long, Integer>> coOccurrence = new HashMap<>();
        for (Set<Long> productIds : orderProducts.values()) {
            List<Long> items = new ArrayList<>(productIds);
            for (int i = 0; i < items.size(); i++) {
                for (int j = 0; j < items.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    Long left = items.get(i);
                    Long right = items.get(j);
                    coOccurrence.computeIfAbsent(left, key -> new HashMap<>()).merge(right, 1, Integer::sum);
                }
            }
        }

        Map<Long, Double> scores = new HashMap<>();
        for (Long sourceProductId : userPurchased) {
            Map<Long, Integer> related = coOccurrence.getOrDefault(sourceProductId, Map.of());
            double preferenceWeight = Math.max(1, userPreferenceWeight.getOrDefault(sourceProductId, 1));
            int sourceFrequency = Math.max(1, itemOrderFrequency.getOrDefault(sourceProductId, 1));

            for (Map.Entry<Long, Integer> entry : related.entrySet()) {
                Long candidateId = entry.getKey();
                if (userPurchased.contains(candidateId) || !productMap.containsKey(candidateId)) {
                    continue;
                }
                int candidateFrequency = Math.max(1, itemOrderFrequency.getOrDefault(candidateId, 1));
                double similarity = entry.getValue() / Math.sqrt((double) sourceFrequency * candidateFrequency);
                scores.merge(candidateId, similarity * preferenceWeight, Double::sum);
            }
        }

        List<Product> recommendations;
        if (scores.isEmpty()) {
            recommendations = buildFallbackRecommendations(activeProducts, userPurchased, itemSalesWeight);
        } else {
            recommendations = scores.entrySet().stream()
                    .sorted((a, b) -> {
                        int byScore = Double.compare(b.getValue(), a.getValue());
                        if (byScore != 0) {
                            return byScore;
                        }
                        int bySales = Integer.compare(
                                itemSalesWeight.getOrDefault(b.getKey(), 0),
                                itemSalesWeight.getOrDefault(a.getKey(), 0));
                        if (bySales != 0) {
                            return bySales;
                        }
                        return Long.compare(b.getKey(), a.getKey());
                    })
                    .map(entry -> productMap.get(entry.getKey()))
                    .filter(item -> item != null)
                    .collect(Collectors.toList());
        }

        return paginateProducts(recommendations, safePage, safeSize);
    }

    private Page<Product> paginateProducts(List<Product> products, int page, int size) {
        Page<Product> result = new Page<>(page, size);
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(products.size(), fromIndex + size);
        List<Product> records = fromIndex >= products.size() ? List.of() : products.subList(fromIndex, toIndex);
        result.setRecords(records);
        result.setTotal(products.size());
        return result;
    }

    private List<Product> buildFallbackRecommendations(List<Product> products,
                                                       Set<Long> excludedIds,
                                                       Map<Long, Integer> itemSalesWeight) {
        return products.stream()
                .filter(item -> item.getId() != null)
                .filter(item -> excludedIds == null || !excludedIds.contains(item.getId()))
                .sorted(Comparator
                        .comparingInt((Product item) -> itemSalesWeight.getOrDefault(item.getId(), 0)).reversed()
                .thenComparing(Product::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @CacheEvict(cacheNames = {"product:list", "product:detail"}, allEntries = true)
    public void evictProductCache() {
        // 商品新增、编辑、删除后统一清理热点缓存，避免前台读取旧数据。
    }
}
