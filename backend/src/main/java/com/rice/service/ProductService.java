package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.dto.ProductReviewDTO;
import com.rice.entity.Order;
import com.rice.entity.OrderItem;
import com.rice.entity.Product;
import com.rice.entity.Review;
import com.rice.entity.User;
import com.rice.mapper.OrderItemMapper;
import com.rice.mapper.OrderMapper;
import com.rice.mapper.ProductMapper;
import com.rice.mapper.ReviewMapper;
import com.rice.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
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
    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;

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

// 将商品详情按商品ID缓存到 product:detail，key 为方法参数 id
    public Product getById(Long id) {
        // 根据主键查询商品
        return productMapper.selectById(id);
    }

    // 基于用户历史购买行为做商品推荐（协同过滤 + 兜底推荐）
    public Page<Product> recommend(Long userId, int page, int size) {
        // 页码下限保护，最小为 1
        int safePage = Math.max(page, 1);
        // 每页大小保护，最小 1，最大 20，防止一次查太多
        int safeSize = Math.max(1, Math.min(size, 20));

        // 查询所有上架商品（status=1）
        List<Product> activeProducts = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));

        // 初始化返回分页对象
        Page<Product> result = new Page<>(safePage, safeSize);

        // 如果没有上架商品，直接返回空分页
        if (activeProducts == null || activeProducts.isEmpty()) {
            // 结果记录置空列表
            result.setRecords(List.of());
            // 总数设为 0
            result.setTotal(0);
            // 返回空结果
            return result;
        }

        // 构建 商品ID -> 商品对象 的映射，后续快速取商品详情
        Map<Long, Product> productMap = activeProducts.stream()
                // 过滤掉 ID 为空的数据，避免 Map key 异常
                .filter(item -> item.getId() != null)
                // 收集为 LinkedHashMap，保持插入顺序
                .collect(Collectors.toMap(Product::getId, item -> item, (a, b) -> a, LinkedHashMap::new));

        // 查询“已进入交易流程”的订单（1,2,3,4 状态），只取订单ID和用户ID减少字段开销
        List<Order> paidOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, 1, 2, 3, 4)
                .select(Order::getId, Order::getUserId));

        // 如果没有可用于推荐训练的订单，走兜底推荐
        if (paidOrders == null || paidOrders.isEmpty()) {
            // 兜底推荐：不排除已购（Set.of()），销量权重为空(Map.of())
            return paginateProducts(buildFallbackRecommendations(activeProducts, Set.of(), Map.of()), safePage, safeSize);
        }

        // 构建 订单ID -> 下单用户ID 映射
        Map<Long, Long> orderUserMap = paidOrders.stream()
                // 过滤订单ID为空的脏数据
                .filter(item -> item.getId() != null)
                // 收集为 Map，key 冲突时保留旧值
                .collect(Collectors.toMap(Order::getId, Order::getUserId, (a, b) -> a));

        // 取全部订单ID集合
        Set<Long> orderIds = orderUserMap.keySet();

        // 批量查询这些订单的订单项，避免逐单查询（N+1）
        List<OrderItem> allItems = orderIds.isEmpty()
                ? List.of()
                : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));

        // 订单ID -> 该订单包含的商品ID集合（用于计算共现矩阵）
        Map<Long, Set<Long>> orderProducts = new HashMap<>();
        // 商品ID -> 被多少个订单包含（订单频次）
        Map<Long, Integer> itemOrderFrequency = new HashMap<>();
        // 商品ID -> 销量权重（按 quantity 累加）
        Map<Long, Integer> itemSalesWeight = new HashMap<>();
        // 当前用户偏好权重（当前用户买过的商品及数量权重）
        Map<Long, Integer> userPreferenceWeight = new HashMap<>();

        // 遍历所有订单项，构建推荐所需统计信息
        for (OrderItem item : allItems) {
            // 过滤无效订单项：订单ID/商品ID为空，或商品不在当前上架商品池
            if (item.getOrderId() == null || item.getProductId() == null || !productMap.containsKey(item.getProductId())) {
                continue;
            }

            // 将商品ID加入对应订单的商品集合
            orderProducts.computeIfAbsent(item.getOrderId(), key -> new HashSet<>()).add(item.getProductId());

            // 累加商品销量权重（数量为空则按1计）
            itemSalesWeight.merge(item.getProductId(), Math.max(1, item.getQuantity() == null ? 1 : item.getQuantity()), Integer::sum);

            // 找到该订单所属用户
            Long ownerId = orderUserMap.get(item.getOrderId());

            // 如果是当前用户的订单，累加用户偏好权重
            if (ownerId != null && ownerId.equals(userId)) {
                userPreferenceWeight.merge(item.getProductId(), Math.max(1, item.getQuantity() == null ? 1 : item.getQuantity()), Integer::sum);
            }
        }

        // 统计每个商品出现在多少个订单中（订单频次）
        for (Set<Long> productIds : orderProducts.values()) {
            for (Long productId : productIds) {
                itemOrderFrequency.merge(productId, 1, Integer::sum);
            }
        }

        // 当前用户买过的商品集合
        Set<Long> userPurchased = userPreferenceWeight.keySet();

        // 如果用户没有购买历史，走“热门兜底推荐”
        if (userPurchased.isEmpty()) {
            return paginateProducts(buildFallbackRecommendations(activeProducts, Set.of(), itemSalesWeight), safePage, safeSize);
        }

        // 共现矩阵：left 商品与 right 商品在同单出现的次数
        Map<Long, Map<Long, Integer>> coOccurrence = new HashMap<>();

        // 对每个订单内商品集合做两两共现统计
        for (Set<Long> productIds : orderProducts.values()) {
            // 转为列表便于双循环
            List<Long> items = new ArrayList<>(productIds);

            // 外层循环选“左商品”
            for (int i = 0; i < items.size(); i++) {
                // 内层循环选“右商品”
                for (int j = 0; j < items.size(); j++) {
                    // 同一商品不计算共现
                    if (i == j) {
                        continue;
                    }

                    // 左商品ID
                    Long left = items.get(i);
                    // 右商品ID
                    Long right = items.get(j);

                    // 共现次数 +1
                    coOccurrence.computeIfAbsent(left, key -> new HashMap<>()).merge(right, 1, Integer::sum);
                }
            }
        }

        // 候选商品最终得分：商品ID -> 分数
        Map<Long, Double> scores = new HashMap<>();

        // 以用户已购商品作为“源商品”进行扩散打分
        for (Long sourceProductId : userPurchased) {
            // 取该源商品的关联商品集合（若无则空Map）
            Map<Long, Integer> related = coOccurrence.getOrDefault(sourceProductId, Map.of());

            // 当前用户对源商品偏好权重（最低1）
            double preferenceWeight = Math.max(1, userPreferenceWeight.getOrDefault(sourceProductId, 1));

            // 源商品订单频次（最低1）
            int sourceFrequency = Math.max(1, itemOrderFrequency.getOrDefault(sourceProductId, 1));

            // 遍历关联候选商品
            for (Map.Entry<Long, Integer> entry : related.entrySet()) {
                // 候选商品ID
                Long candidateId = entry.getKey();

                // 已购商品不再推荐；不在商品池也跳过
                if (userPurchased.contains(candidateId) || !productMap.containsKey(candidateId)) {
                    continue;
                }

                // 候选商品订单频次（最低1）
                int candidateFrequency = Math.max(1, itemOrderFrequency.getOrDefault(candidateId, 1));

                // 余弦风格相似度：共现次数 / sqrt(源频次 * 候选频次)
                double similarity = entry.getValue() / Math.sqrt((double) sourceFrequency * candidateFrequency);

                // 分数累计：相似度 * 用户偏好权重
                scores.merge(candidateId, similarity * preferenceWeight, Double::sum);
            }
        }

        // 最终推荐列表
        List<Product> recommendations;

        // 如果协同过滤分数为空，则走兜底推荐
        if (scores.isEmpty()) {
            recommendations = buildFallbackRecommendations(activeProducts, userPurchased, itemSalesWeight);
        } else {
            // 否则按分数、销量、商品ID排序
            recommendations = scores.entrySet().stream()
                    .sorted((a, b) -> {
                        // 第一排序：分数降序
                        int byScore = Double.compare(b.getValue(), a.getValue());
                        if (byScore != 0) {
                            return byScore;
                        }

                        // 第二排序：销量权重降序
                        int bySales = Integer.compare(
                                itemSalesWeight.getOrDefault(b.getKey(), 0),
                                itemSalesWeight.getOrDefault(a.getKey(), 0));
                        if (bySales != 0) {
                            return bySales;
                        }

                        // 第三排序：ID降序（保证稳定排序）
                        return Long.compare(b.getKey(), a.getKey());
                    })
                    // 映射成商品对象
                    .map(entry -> productMap.get(entry.getKey()))
                    // 过滤空值防御
                    .filter(item -> item != null)
                    // 收集成列表
                    .collect(Collectors.toList());
        }

        // 最终按 page/size 分页返回
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

    public void evictProductCache() {
        // 商品新增、编辑、删除后统一清理热点缓存，避免前台读取旧数据。
    }

    public Page<ProductReviewDTO> listReviews(Long productId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 50));
        Page<Review> reviewPage = reviewMapper.selectPage(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProductId, productId)
                        .orderByDesc(Review::getCreateTime)
                        .orderByDesc(Review::getId)
        );

        List<Review> reviews = reviewPage.getRecords();
        Set<Long> userIds = reviews.stream()
                .map(Review::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, item -> item, (a, b) -> a));

        List<ProductReviewDTO> records = reviews.stream().map(item -> {
            ProductReviewDTO dto = new ProductReviewDTO();
            dto.setId(item.getId());
            dto.setUserId(item.getUserId());
            dto.setRating(item.getRating());
            dto.setContent(item.getContent());
            dto.setCreateTime(item.getCreateTime());
            User user = userMap.get(item.getUserId());
            if (user != null) {
                dto.setUsername(user.getUsername());
                dto.setUserAvatar(user.getAvatar());
            }
            return dto;
        }).collect(Collectors.toList());

        Page<ProductReviewDTO> result = new Page<>(safePage, safeSize);
        result.setTotal(reviewPage.getTotal());
        result.setRecords(records);
        return result;
    }
}
