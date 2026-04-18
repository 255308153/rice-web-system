package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rice.dto.MerchantOrderStatsDTO;
import com.rice.dto.HotProductDTO;
import com.rice.entity.Logistics;
import com.rice.entity.Order;
import com.rice.entity.OrderItem;
import com.rice.entity.Product;
import com.rice.entity.RefundRequest;
import com.rice.entity.Shop;
import com.rice.entity.User;
import com.rice.mapper.LogisticsMapper;
import com.rice.mapper.OrderMapper;
import com.rice.mapper.OrderItemMapper;
import com.rice.mapper.ProductMapper;
import com.rice.mapper.RefundRequestMapper;
import com.rice.mapper.ShopMapper;
import com.rice.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MerchantService {
    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);
    private static final Pattern ORDER_NO_MILLIS_PATTERN = Pattern.compile("(\\d{13})");
    private static final int SUMMARY_AI_TIMEOUT_SECONDS = 4;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private LogisticsMapper logisticsMapper;

    @Autowired
    private RefundRequestMapper refundRequestMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ChatService chatService;

    @CacheEvict(cacheNames = {"product:list", "product:detail"}, allEntries = true)
    public void addProduct(Product product) {
        productMapper.insert(product);
    }

    @CacheEvict(cacheNames = {"product:list", "product:detail"}, allEntries = true)
    public void updateProduct(Product product) {
        productMapper.updateById(product);
    }

    @CacheEvict(cacheNames = {"product:list", "product:detail"}, allEntries = true)
    public void deleteProduct(Long id) {
        productMapper.deleteById(id);
    }

    public List<Order> getMerchantOrders(Long merchantId, Long shopId, Integer status) {
        Long resolvedShopId = resolveShopId(merchantId, shopId);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getShopId, resolvedShopId)
                .orderByDesc(Order::getId);

        if (status != null) {
            if (status == 4) {
                List<Long> refundOrderIds = refundRequestMapper.selectList(
                                new LambdaQueryWrapper<RefundRequest>()
                                        .eq(RefundRequest::getShopId, resolvedShopId))
                        .stream()
                        .map(RefundRequest::getOrderId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                if (refundOrderIds.isEmpty()) {
                    return Collections.emptyList();
                }
                wrapper.in(Order::getId, refundOrderIds);
            } else {
                wrapper.eq(Order::getStatus, status);
            }
        }

        List<Order> orders = orderMapper.selectList(wrapper);
        fillMissingCreateTime(orders);
        return orders;
    }

    @Transactional
    public void shipOrder(Long merchantId, Long orderId, String company, String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            throw new RuntimeException("请填写物流单号");
        }

        Long shopId = resolveShopId(merchantId, null);
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!Objects.equals(order.getShopId(), shopId)) {
            throw new RuntimeException("无权限操作该订单");
        }
        if (!Objects.equals(order.getStatus(), 1)) {
            throw new RuntimeException("仅待发货订单可执行发货");
        }

        Logistics logistics = logisticsMapper.selectOne(
                new LambdaQueryWrapper<Logistics>()
                        .eq(Logistics::getOrderId, orderId)
                        .last("LIMIT 1")
        );
        if (logistics == null) {
            logistics = new Logistics();
            logistics.setOrderId(orderId);
        }
        logistics.setCompany(StringUtils.hasText(company) ? company.trim() : "默认物流");
        logistics.setTrackingNumber(trackingNumber.trim());
        if (logistics.getId() == null) {
            logisticsMapper.insert(logistics);
        } else {
            logisticsMapper.updateById(logistics);
        }

        Order update = new Order();
        update.setId(orderId);
        update.setStatus(2);
        orderMapper.updateById(update);
    }

    public List<RefundRequest> getMerchantRefunds(Long merchantId, Integer status) {
        Long shopId = resolveShopId(merchantId, null);

        LambdaQueryWrapper<RefundRequest> wrapper = new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getShopId, shopId)
                .orderByDesc(RefundRequest::getCreateTime)
                .orderByDesc(RefundRequest::getId);
        if (status != null && status >= 0) {
            wrapper.eq(RefundRequest::getStatus, status);
        }

        List<RefundRequest> refunds = refundRequestMapper.selectList(wrapper);
        fillRefundExtraFields(refunds);
        return refunds;
    }

    @Transactional
    public void processRefund(Long merchantId, Long refundId, Integer status, String merchantRemark) {
        if (status == null || (status != 1 && status != 2)) {
            throw new RuntimeException("退款处理状态不合法");
        }

        Long shopId = resolveShopId(merchantId, null);
        RefundRequest refund = refundRequestMapper.selectById(refundId);
        if (refund == null) {
            throw new RuntimeException("退款申请不存在");
        }
        if (!Objects.equals(refund.getShopId(), shopId)) {
            throw new RuntimeException("无权限处理该退款申请");
        }
        if (!Objects.equals(refund.getStatus(), 0)) {
            throw new RuntimeException("该退款申请已处理");
        }
        if (status == 2 && !StringUtils.hasText(merchantRemark)) {
            throw new RuntimeException("拒绝退款时请填写处理说明");
        }

        RefundRequest update = new RefundRequest();
        update.setId(refundId);
        update.setStatus(status);
        update.setMerchantRemark(StringUtils.hasText(merchantRemark) ? merchantRemark.trim() : null);
        update.setAuditTime(LocalDateTime.now());
        refundRequestMapper.updateById(update);

        if (status == 1 && refund.getOrderId() != null) {
            Order order = orderMapper.selectById(refund.getOrderId());
            if (order != null && Objects.equals(order.getShopId(), shopId)) {
                Order orderUpdate = new Order();
                orderUpdate.setId(order.getId());
                // 售后状态：用于商户订单筛选
                orderUpdate.setStatus(4);
                orderMapper.updateById(orderUpdate);
            }
        }
    }

    public MerchantOrderStatsDTO getOrderStats(Long merchantId) {
        Long shopId = resolveShopId(merchantId, null);
        List<Order> paidOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getShopId, shopId)
                .in(Order::getStatus, 1, 2, 3, 4));
        fillMissingCreateTime(paidOrders);

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        List<Order> monthOrders = paidOrders.stream()
                .filter(order -> order.getCreateTime() != null && !order.getCreateTime().toLocalDate().isBefore(monthStart))
                .collect(Collectors.toList());

        List<Order> dayOrders = monthOrders.stream()
                .filter(order -> order.getCreateTime().toLocalDate().isEqual(today))
                .collect(Collectors.toList());

        Set<Long> monthOrderIds = monthOrders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> dayOrderIds = dayOrders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        MerchantOrderStatsDTO stats = new MerchantOrderStatsDTO();
        stats.setDaySalesAmount(sumOrderAmount(dayOrders));
        stats.setMonthSalesAmount(sumOrderAmount(monthOrders));
        stats.setDaySalesVolume(sumItemQuantity(dayOrderIds));
        stats.setMonthSalesVolume(sumItemQuantity(monthOrderIds));
        return stats;
    }

    public List<HotProductDTO> getTopProducts(Long merchantId, int limit) {
        Long shopId = resolveShopId(merchantId, null);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<HotProductDTO> data = orderItemMapper.selectHotProductsByShop(shopId, safeLimit);
        if (data != null && !data.isEmpty()) {
            return data;
        }
        return buildFallbackTopProducts(shopId, safeLimit);
    }

    public Map<String, Object> getSalesAssistantSummary(Long merchantId) {
        Map<String, Object> summary = buildSalesAssistantPayload(merchantId);
        String fallbackReply = buildLocalSalesSummary(summary);
        summary.put("assistantReply", fallbackReply);
        try {
            String aiReply = CompletableFuture
                    .supplyAsync(() -> chatService.generateMerchantSalesSummary(merchantId, summary))
                    .completeOnTimeout(fallbackReply, SUMMARY_AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.warn("商户销售摘要AI生成失败，使用本地摘要兜底: {}", ex.getMessage());
                        return fallbackReply;
                    })
                    .join();
            if (StringUtils.hasText(aiReply)) {
                summary.put("assistantReply", aiReply);
            }
        } catch (Exception e) {
            log.warn("商户销售摘要AI生成异常，使用本地摘要兜底: {}", e.getMessage());
        }
        return summary;
    }

    public String chatAssistant(Long merchantId, String message) {
        Map<String, Object> summary = buildSalesAssistantPayload(merchantId);
        return chatService.chatForMerchantAssistant(merchantId, message, summary);
    }

    private Long resolveShopId(Long merchantId, Long shopId) {
        if (merchantId == null) {
            throw new RuntimeException("未登录");
        }

        User merchant = userMapper.selectById(merchantId);
        if (merchant == null || !Objects.equals(merchant.getStatus(), 1)) {
            throw new RuntimeException("商户账号不可用");
        }
        if (!"MERCHANT".equalsIgnoreCase(merchant.getRole())) {
            throw new RuntimeException("无商户权限");
        }

        Shop myShop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getUserId, merchantId)
                .last("LIMIT 1"));
        if (myShop == null) {
            myShop = new Shop();
            myShop.setUserId(merchantId);
            myShop.setName(StringUtils.hasText(merchant.getUsername()) ? merchant.getUsername() + "的店铺" : "默认商户店铺");
            myShop.setDescription("系统已自动创建默认店铺，请在店铺管理中完善信息");
            myShop.setStatus(1);
            myShop.setRating(new BigDecimal("5.0"));
            shopMapper.insert(myShop);
        }
        if (myShop == null) {
            throw new RuntimeException("请先完善店铺信息");
        }
        if (shopId != null && !Objects.equals(myShop.getId(), shopId)) {
            throw new RuntimeException("无权限访问该店铺");
        }
        return myShop.getId();
    }

    private Map<String, Object> buildSalesAssistantPayload(Long merchantId) {
        Long shopId = resolveShopId(merchantId, null);
        Shop shop = shopMapper.selectById(shopId);
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getShopId, shopId)
                .orderByDesc(Order::getId));
        fillMissingCreateTime(orders);

        List<Order> paidOrders = orders.stream()
                .filter(order -> order.getStatus() != null && List.of(1, 2, 3, 4).contains(order.getStatus()))
                .collect(Collectors.toList());
        Set<Long> uniqueBuyerIds = paidOrders.stream()
                .map(Order::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        MerchantOrderStatsDTO orderStats = getOrderStats(merchantId);
        List<HotProductDTO> topProducts = getTopProducts(merchantId, 5);

        int pendingOrders = (int) orders.stream().filter(order -> Objects.equals(order.getStatus(), 1)).count();
        int completedOrders = (int) orders.stream().filter(order -> Objects.equals(order.getStatus(), 3)).count();
        int afterSalesOrders = (int) orders.stream().filter(order -> Objects.equals(order.getStatus(), 4)).count();
        BigDecimal totalSalesAmount = sumOrderAmount(paidOrders);
        BigDecimal averageOrderValue = paidOrders.isEmpty()
                ? BigDecimal.ZERO
                : totalSalesAmount.divide(new BigDecimal(paidOrders.size()), 2, RoundingMode.HALF_UP);

        List<Map<String, Object>> topProductList = new ArrayList<>();
        for (HotProductDTO item : topProducts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", item.getProductId());
            row.put("productName", item.getProductName());
            row.put("salesCount", item.getSalesCount());
            row.put("salesAmount", item.getSalesAmount());
            topProductList.add(row);
        }

        List<Map<String, Object>> recentOrders = paidOrders.stream()
                .limit(8)
                .map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("orderId", order.getId());
                    row.put("orderNo", order.getOrderNo());
                    row.put("totalPrice", order.getTotalPrice());
                    row.put("status", order.getStatus());
                    row.put("createTime", order.getCreateTime());
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> shopInfo = new LinkedHashMap<>();
        if (shop != null) {
            shopInfo.put("id", shop.getId());
            shopInfo.put("name", shop.getName());
            shopInfo.put("description", shop.getDescription());
            shopInfo.put("contact", shop.getContact());
            shopInfo.put("rating", shop.getRating());
            shopInfo.put("status", shop.getStatus());
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalOrders", orders.size());
        metrics.put("paidOrders", paidOrders.size());
        metrics.put("pendingOrders", pendingOrders);
        metrics.put("completedOrders", completedOrders);
        metrics.put("afterSalesOrders", afterSalesOrders);
        metrics.put("daySalesAmount", orderStats.getDaySalesAmount());
        metrics.put("monthSalesAmount", orderStats.getMonthSalesAmount());
        metrics.put("daySalesVolume", orderStats.getDaySalesVolume());
        metrics.put("monthSalesVolume", orderStats.getMonthSalesVolume());
        metrics.put("totalSalesAmount", totalSalesAmount);
        metrics.put("averageOrderValue", averageOrderValue);
        metrics.put("uniqueBuyerCount", uniqueBuyerIds.size());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shop", shopInfo);
        payload.put("metrics", metrics);
        payload.put("topProducts", topProductList);
        payload.put("recentOrders", recentOrders);
        payload.put("generatedAt", LocalDateTime.now());
        return payload;
    }

    private String buildLocalSalesSummary(Map<String, Object> summary) {
        Map<String, Object> shop = toObjectMap(summary == null ? null : summary.get("shop"));
        Map<String, Object> metrics = toObjectMap(summary == null ? null : summary.get("metrics"));
        List<Map<String, Object>> topProducts = toObjectMapList(summary == null ? null : summary.get("topProducts"));

        String shopName = String.valueOf(shop.getOrDefault("name", "当前店铺"));
        int totalOrders = toInt(metrics.get("totalOrders"));
        int paidOrders = toInt(metrics.get("paidOrders"));
        int pendingOrders = toInt(metrics.get("pendingOrders"));
        int uniqueBuyerCount = toInt(metrics.get("uniqueBuyerCount"));
        String monthSalesAmount = toMoney(metrics.get("monthSalesAmount"));

        StringBuilder builder = new StringBuilder();
        builder.append("【").append(shopName).append("经营快照】")
                .append("累计订单 ").append(totalOrders).append(" 单，")
                .append("成交 ").append(paidOrders).append(" 单，")
                .append("成交买家 ").append(uniqueBuyerCount).append(" 人，")
                .append("本月销售额 ¥").append(monthSalesAmount).append("，")
                .append("待处理订单 ").append(pendingOrders).append(" 单。");

        if (topProducts == null || topProducts.isEmpty()) {
            builder.append("\n当前暂无热销商品统计，可先确保商品已上架并完成订单成交，后续系统会自动更新热销榜。");
        } else {
            Map<String, Object> first = topProducts.get(0);
            String productName = String.valueOf(first.getOrDefault("productName", "主推商品"));
            long salesCount = toLong(first.get("salesCount"));
            builder.append("\n当前热销商品：").append(productName).append("（销量 ").append(salesCount).append(" 件）。");
        }

        builder.append("\n你可以继续提问：如“如何提升复购”或“本周主推什么商品”。");
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toObjectMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toObjectMapList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Map<?, ?> mapItem) {
                result.add((Map<String, Object>) mapItem);
            }
        }
        return result;
    }

    private int toInt(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String toMoney(Object value) {
        if (value == null) {
            return "0.00";
        }
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value));
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception ignored) {
            return "0.00";
        }
    }

    /**
     * 当店铺暂无成交订单时，回退展示店铺商品列表（按历史销量/创建顺序）。
     * 这样经营助手页不会长期显示“暂无热销商品数据”。
     */
    private List<HotProductDTO> buildFallbackTopProducts(Long shopId, int limit) {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getShopId, shopId)
                .orderByDesc(Product::getSales)
                .orderByDesc(Product::getId)
                .last("LIMIT " + limit));
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        List<HotProductDTO> fallback = new ArrayList<>();
        for (Product product : products) {
            HotProductDTO dto = new HotProductDTO();
            dto.setProductId(product.getId());
            dto.setProductName(product.getName());

            long salesCount = product.getSales() == null ? 0L : Math.max(product.getSales(), 0);
            dto.setSalesCount(salesCount);

            BigDecimal unitPrice = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
            dto.setSalesAmount(unitPrice.multiply(BigDecimal.valueOf(salesCount)));
            fallback.add(dto);
        }
        return fallback;
    }

    private void fillRefundExtraFields(List<RefundRequest> refunds) {
        if (refunds == null || refunds.isEmpty()) {
            return;
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (RefundRequest refund : refunds) {
            if (refund.getUserId() != null) {
                userIds.add(refund.getUserId());
            }
            if (refund.getOrderId() != null) {
                orderIds.add(refund.getOrderId());
            }
        }

        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        Map<Long, Order> orderMap = orderIds.isEmpty()
                ? Collections.emptyMap()
                : orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, order -> order, (a, b) -> a));

        for (RefundRequest refund : refunds) {
            User user = userMap.get(refund.getUserId());
            if (user != null) {
                refund.setUsername(user.getUsername());
            }
            Order order = orderMap.get(refund.getOrderId());
            if (order != null) {
                refund.setOrderNo(order.getOrderNo());
            }
        }
    }

    private BigDecimal sumOrderAmount(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return orders.stream()
                .map(Order::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Integer sumItemQuantity(Set<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds)
        );
        return items.stream()
                .map(OrderItem::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void fillMissingCreateTime(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (Order order : orders) {
            if (order.getCreateTime() != null) {
                continue;
            }
            LocalDateTime parsedTime = parseCreateTimeFromOrderNo(order.getOrderNo());
            if (parsedTime != null) {
                order.setCreateTime(parsedTime);
            }
        }
    }

    private LocalDateTime parseCreateTimeFromOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        try {
            Matcher matcher = ORDER_NO_MILLIS_PATTERN.matcher(orderNo);
            if (!matcher.find()) {
                return null;
            }
            long millis = Long.parseLong(matcher.group(1));
            if (millis <= 0) {
                return null;
            }
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }
}
