package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.dto.OrderItemDetailDTO;
import com.rice.entity.*;
import com.rice.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
    // 订单号里提取毫秒时间戳（用于兜底恢复 createTime）
    private static final Pattern ORDER_NO_MILLIS_PATTERN = Pattern.compile("(\\d{13})");
    // 订单状态常量：与前端状态标签、状态流转逻辑保持一致
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

    /**
     * 创建订单主流程（事务）：
     * 1) 校验地址归属与商品明细
     * 2) 校验商品在售、库存与单店铺约束
     * 3) 计算总价并写入订单主表
     * 4) 写入订单明细并扣减库存/累计销量
     */
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
        // 由商品反查店铺，确保一次下单仅包含同一店铺商品
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
            // 下单前库存校验，防止超卖
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

            // 使用数据库原子更新避免并发超卖：
            // update ... where stock >= quantity
            int updated = productMapper.decreaseStockAndIncreaseSales(item.getProductId(), item.getQuantity());
            if (updated <= 0) {
                throw new RuntimeException("商品库存不足，请刷新后重试");
            }
        }
        return order;
    }

    public List<Order> listByUser(Long userId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId));
        // 兜底填充历史脏数据的 createTime（从订单号推导）
        fillMissingCreateTime(orders);
        // 补充每个订单是否“已评价”的派生状态
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
        // 分页参数兜底，避免 page/size 非法值导致异常
        Page<Order> result = orderMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        fillMissingCreateTime(result.getRecords());
        fillReviewedState(userId, result.getRecords());
        return result;
    }

    /**
     * 支付：仅允许“待支付 -> 待发货”。
     */
    public void pay(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderStatus(order, STATUS_PENDING_PAYMENT, "仅待付款订单可支付");
        order.setStatus(STATUS_PENDING_SHIPMENT);
        orderMapper.updateById(order);
    }

    /**
     * 确认收货：仅允许“待收货 -> 已完成”。
     */
    public void confirm(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderStatus(order, STATUS_PENDING_RECEIPT, "仅待收货订单可确认收货");
        order.setStatus(STATUS_COMPLETED);
        orderMapper.updateById(order);
    }

    /**
     * 订单评价：
     * - 校验订单归属、状态
     * - 校验评价商品属于该订单
     * - 防止重复评价
     */
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

    /**
     * 查询订单商品明细并补齐商品名、首图等展示字段。
     */
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

    /**
     * 批量标记订单是否已评价，减少前端二次查询。
     */
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

    /**
     * 申请退款（事务）：
     * - 校验订单归属和状态
     * - 校验退款时效（读取系统配置）
     * - 校验金额范围与重复申请
     * - 写入退款申请表
     */
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
        try {
            refundRequestMapper.insert(refund);
        } catch (DuplicateKeyException ex) {
            // 并发场景下由数据库唯一约束兜底，避免重复待处理退款单
            throw new RuntimeException("该订单已有待处理退款申请");
        }
    }

    /**
     * 查询当前用户退款记录，并补充订单号用于前端展示。
     */
    public List<RefundRequest> listRefundsByUser(Long userId) {
        List<RefundRequest> refunds = refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getUserId, userId)
                .orderByDesc(RefundRequest::getCreateTime)
                .orderByDesc(RefundRequest::getId));
        fillRefundOrderNo(refunds);
        return refunds;
    }

    // 退款记录关联订单号，避免前端二次 join
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

    // 历史数据兜底：createTime 为空时尝试由订单号解析
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

    // 从订单号中的 13 位毫秒时间戳反推出创建时间
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

    // 从系统配置读取退款时效（trade.refundDays），读取失败则默认 7 天
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

    // 商品图片字段兼容数组字符串/逗号分隔字符串两种存储格式
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

    // 统一状态校验入口，减少重复 if 逻辑
    private void assertOrderStatus(Order order, int expectedStatus, String message) {
        if (!Objects.equals(order.getStatus(), expectedStatus)) {
            throw new RuntimeException(message);
        }
    }

    // 订单号生成策略：毫秒时间戳 + userId 后三位 + 四位随机数
    private String generateOrderNo(Long userId) {
        long suffix = Math.abs((userId == null ? 0L : userId) % 1000);
        int random = (int) (Math.random() * 9000) + 1000;
        return System.currentTimeMillis() + String.format("%03d%04d", suffix, random);
    }
}
