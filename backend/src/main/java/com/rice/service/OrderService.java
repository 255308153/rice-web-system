package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.dto.OrderItemDetailDTO;
import com.rice.entity.*;
import com.rice.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final Pattern ORDER_NO_MILLIS_PATTERN = Pattern.compile("(\\d{13})");
    private static final int STATUS_PENDING_PAYMENT = 0;
    private static final int STATUS_PENDING_SHIPMENT = 1;
    private static final int STATUS_PENDING_RECEIPT = 2;
    private static final int STATUS_COMPLETED = 3;
    private static final int STATUS_AFTER_SALE = 4;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final AddressMapper addressMapper;
    private final ReviewMapper reviewMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final SystemConfigMapper systemConfigMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Order create(Long userId, Long shopId, Long addressId, List<OrderItem> items) {
        if (addressId == null) {
            throw new RuntimeException("请先选择收货地址");
        }

        Address address = addressMapper.selectById(addressId);
        if (address == null || !userId.equals(address.getUserId())) {
            throw new RuntimeException("收货地址无效，请重新选择");
        }

        if (items == null || items.isEmpty()) {
            throw new RuntimeException("订单商品不能为空");
        }

        BigDecimal total = BigDecimal.ZERO;
        Long resolvedShopId = null;
        List<OrderItem> validatedItems = new ArrayList<>();

        for (OrderItem item : items) {
            if (item.getProductId() == null) {
                throw new RuntimeException("商品信息不完整");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new RuntimeException("商品数量必须大于0");
            }

            Product product = productMapper.selectById(item.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在或已下架");
            }
            if (!Objects.equals(product.getStatus(), 1)) {
                throw new RuntimeException("商品不存在或已下架");
            }
            int currentStock = product.getStock() == null ? 0 : product.getStock();
            if (currentStock < item.getQuantity()) {
                throw new RuntimeException("商品库存不足：" + product.getName());
            }

            if (resolvedShopId == null) {
                resolvedShopId = product.getShopId();
            } else if (!resolvedShopId.equals(product.getShopId())) {
                throw new RuntimeException("暂不支持跨店铺下单，请分开提交");
            }

            OrderItem validated = new OrderItem();
            validated.setProductId(product.getId());
            validated.setQuantity(item.getQuantity());
            validated.setPrice(product.getPrice());
            validatedItems.add(validated);

            total = total.add(product.getPrice().multiply(new BigDecimal(item.getQuantity())));
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo(userId));
        order.setUserId(userId);
        order.setShopId(resolvedShopId != null ? resolvedShopId : shopId);
        order.setTotalPrice(total);
        order.setAddressId(addressId);
        order.setStatus(STATUS_PENDING_PAYMENT);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);

        for (OrderItem item : validatedItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);

            Product product = productMapper.selectById(item.getProductId());
            if (product != null) {
                product.setStock(Math.max(0, (product.getStock() != null ? product.getStock() : 0) - item.getQuantity()));
                product.setSales((product.getSales() != null ? product.getSales() : 0) + item.getQuantity());
                productMapper.updateById(product);
            }
        }
        return order;
    }

    public List<Order> listByUser(Long userId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId));
        fillMissingCreateTime(orders);
        fillReviewedState(userId, orders);
        return orders;
    }

    public Page<Order> pageByUser(Long userId, int page, int size, Integer status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId);
        if (status != null && status >= 0) {
            wrapper.eq(Order::getStatus, status);
        }
        Page<Order> result = orderMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        fillMissingCreateTime(result.getRecords());
        fillReviewedState(userId, result.getRecords());
        return result;
    }

    public void pay(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderStatus(order, STATUS_PENDING_PAYMENT, "仅待付款订单可支付");
        order.setStatus(STATUS_PENDING_SHIPMENT);
        orderMapper.updateById(order);
    }

    public void confirm(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderStatus(order, STATUS_PENDING_RECEIPT, "仅待收货订单可确认收货");
        order.setStatus(STATUS_COMPLETED);
        orderMapper.updateById(order);
    }

    public void review(Long orderId, Long userId, Long productId, Integer rating, String content) {
        if (orderId == null || userId == null || productId == null) {
            throw new RuntimeException("评价参数不完整");
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new RuntimeException("无权评价该订单");
        }
        if (!Objects.equals(order.getStatus(), STATUS_COMPLETED)) {
            throw new RuntimeException("仅已完成订单可评价");
        }

        OrderItem orderItem = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .eq(OrderItem::getProductId, productId)
                .last("LIMIT 1"));
        if (orderItem == null) {
            throw new RuntimeException("该商品不在订单内");
        }

        Review existed = reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId)
                .eq(Review::getUserId, userId)
                .last("LIMIT 1"));
        if (existed != null) {
            throw new RuntimeException("该订单已评价");
        }

        int finalRating = rating == null ? 5 : Math.max(1, Math.min(5, rating));
        Review review = new Review();
        review.setOrderId(orderId);
        review.setUserId(userId);
        review.setProductId(productId);
        review.setRating(finalRating);
        review.setContent(StringUtils.hasText(content) ? content.trim() : "");
        reviewMapper.insert(review);
    }

    public List<OrderItemDetailDTO> listOrderItems(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new RuntimeException("无权查看该订单商品");
        }

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> productIds = items.stream()
                .map(OrderItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Product> productMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        List<OrderItemDetailDTO> result = new ArrayList<>();
        for (OrderItem item : items) {
            OrderItemDetailDTO dto = new OrderItemDetailDTO();
            dto.setId(item.getId());
            dto.setOrderId(item.getOrderId());
            dto.setProductId(item.getProductId());
            dto.setQuantity(item.getQuantity());
            dto.setPrice(item.getPrice());

            Product product = productMap.get(item.getProductId());
            if (product != null) {
                dto.setProductName(product.getName());
                dto.setProductImage(extractFirstImage(product.getImages()));
            }
            result.add(dto);
        }
        return result;
    }

    private void fillReviewedState(Long userId, List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        Set<Long> orderIds = orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            orders.forEach(order -> order.setReviewed(false));
            return;
        }

        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .in(Review::getOrderId, orderIds)
                .select(Review::getOrderId));
        Set<Long> reviewedOrderIds = reviews.stream()
                .map(Review::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        orders.forEach(order -> order.setReviewed(reviewedOrderIds.contains(order.getId())));
    }

    @Transactional
    public void applyRefund(Long orderId, Long userId, String reason, BigDecimal amount) {
        if (!StringUtils.hasText(reason)) {
            throw new RuntimeException("请填写退款原因");
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new RuntimeException("无权申请该订单退款");
        }
        if (!Set.of(STATUS_PENDING_SHIPMENT, STATUS_PENDING_RECEIPT).contains(order.getStatus())) {
            throw new RuntimeException("当前订单状态不支持退款");
        }
        int refundDays = resolveRefundDays();
        LocalDateTime orderTime = order.getCreateTime();
        if (orderTime == null) {
            orderTime = parseCreateTimeFromOrderNo(order.getOrderNo());
        }
        if (orderTime != null && orderTime.plusDays(refundDays).isBefore(LocalDateTime.now())) {
            throw new RuntimeException("已超过退款时效（" + refundDays + "天）");
        }

        RefundRequest pending = refundRequestMapper.selectOne(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .eq(RefundRequest::getUserId, userId)
                .eq(RefundRequest::getStatus, 0)
                .last("LIMIT 1"));
        if (pending != null) {
            throw new RuntimeException("该订单已有待处理退款申请");
        }

        BigDecimal maxAmount = order.getTotalPrice() == null ? BigDecimal.ZERO : order.getTotalPrice();
        BigDecimal finalAmount = amount == null ? maxAmount : amount;
        if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("退款金额必须大于0");
        }
        if (maxAmount.compareTo(BigDecimal.ZERO) > 0 && finalAmount.compareTo(maxAmount) > 0) {
            throw new RuntimeException("退款金额不能大于订单金额");
        }

        RefundRequest refund = new RefundRequest();
        refund.setOrderId(orderId);
        refund.setShopId(order.getShopId());
        refund.setUserId(userId);
        refund.setReason(reason.trim());
        refund.setAmount(finalAmount);
        refund.setStatus(0);
        refund.setCreateTime(LocalDateTime.now());
        refund.setUpdateTime(LocalDateTime.now());
        refundRequestMapper.insert(refund);
    }

    public List<RefundRequest> listRefundsByUser(Long userId) {
        List<RefundRequest> refunds = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getUserId, userId)
                .orderByDesc(RefundRequest::getCreateTime)
                .orderByDesc(RefundRequest::getId));
        fillRefundOrderNo(refunds);
        return refunds;
    }

    private void fillRefundOrderNo(List<RefundRequest> refunds) {
        if (refunds == null || refunds.isEmpty()) {
            return;
        }
        Set<Long> orderIds = refunds.stream()
                .map(RefundRequest::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return;
        }
        Map<Long, Order> orderMap = orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, order -> order, (a, b) -> a));
        for (RefundRequest refund : refunds) {
            Order order = orderMap.get(refund.getOrderId());
            if (order != null) {
                refund.setOrderNo(order.getOrderNo());
            }
        }
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
        if (orderNo == null || orderNo.isEmpty()) {
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

    private int resolveRefundDays() {
        int defaultDays = 7;
        try {
            SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, "trade")
                    .last("LIMIT 1"));
            if (config == null || !StringUtils.hasText(config.getConfigValue())) {
                return defaultDays;
            }
            JsonNode node = objectMapper.readTree(config.getConfigValue());
            int configured = node.path("refundDays").asInt(defaultDays);
            return Math.max(1, configured);
        } catch (Exception e) {
            return defaultDays;
        }
    }

    private String extractFirstImage(String rawImages) {
        if (!StringUtils.hasText(rawImages)) {
            return "";
        }
        String raw = rawImages.trim();
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        if (raw.startsWith("[")) {
            String cleaned = raw.replace("[", "").replace("]", "").replace("\"", "").trim();
            if (!StringUtils.hasText(cleaned)) {
                return "";
            }
            String[] parts = cleaned.split(",");
            return parts.length > 0 ? parts[0].trim() : "";
        }
        if (raw.contains(",")) {
            return raw.split(",")[0].trim();
        }
        return raw;
    }

    private void assertOrderStatus(Order order, int expectedStatus, String message) {
        if (!Objects.equals(order.getStatus(), expectedStatus)) {
            throw new RuntimeException(message);
        }
    }

    private String generateOrderNo(Long userId) {
        long suffix = Math.abs((userId == null ? 0L : userId) % 1000);
        int random = (int) (Math.random() * 9000) + 1000;
        return System.currentTimeMillis() + String.format("%03d%04d", suffix, random);
    }
}
