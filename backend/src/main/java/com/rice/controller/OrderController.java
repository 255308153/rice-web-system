package com.rice.controller;

import com.rice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.dto.OrderItemDetailDTO;
import com.rice.entity.Order;
import com.rice.entity.OrderItem;
import com.rice.entity.RefundRequest;
import com.rice.service.OrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    /**
     * 创建订单：
     * userId 从 JWT 过滤器写入的 request attribute 获取，
     * 请求体中传地址与商品明细。
     */
    @PostMapping
    public Result<Order> create(@RequestBody CreateOrderRequest req, @RequestAttribute Long userId) {
        return Result.success(orderService.create(userId, req.getShopId(), req.getAddressId(), req.getItems()));
    }

    /**
     * 查询当前用户订单列表（不分页）。
     */
    @GetMapping
    public Result<List<Order>> list(@RequestAttribute Long userId) {
        return Result.success(orderService.listByUser(userId));
    }

    /**
     * 查询当前用户指定订单的商品明细（用于订单卡片与评价弹窗）。
     */
    @GetMapping("/{id}/items")
    public Result<List<OrderItemDetailDTO>> listItems(@PathVariable Long id, @RequestAttribute Long userId) {
        return Result.success(orderService.listOrderItems(userId, id));
    }

    /**
     * 查询当前用户订单分页列表：
     * status = -1 视为不按状态过滤。
     */
    @GetMapping("/page")
    public Result<Page<Order>> page(@RequestAttribute Long userId,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "8") int size,
                                    @RequestParam(defaultValue = "-1") Integer status) {
        Integer queryStatus = status != null && status >= 0 ? status : null;
        return Result.success(orderService.pageByUser(userId, page, size, queryStatus));
    }

    /**
     * 支付订单：状态从“待支付”推进到“待发货”。
     */
    @PutMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        orderService.pay(id);
        return Result.success();
    }

    /**
     * 确认收货：状态从“待收货”推进到“已完成”。
     */
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        orderService.confirm(id);
        return Result.success();
    }

    /**
     * 订单评价：只允许订单所属用户在已完成状态下评价。
     */
    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @RequestBody ReviewRequest req, @RequestAttribute Long userId) {
        orderService.review(id, userId, req.getProductId(), req.getRating(), req.getContent());
        return Result.success();
    }

    /**
     * 申请退款：校验退款时效、金额范围与重复申请。
     */
    @PostMapping("/{id}/refund")
    public Result<Void> applyRefund(@PathVariable Long id,
                                    @RequestBody RefundApplyRequest req,
                                    @RequestAttribute Long userId) {
        orderService.applyRefund(id, userId, req.getReason(), req.getAmount());
        return Result.success();
    }

    /**
     * 查询当前用户退款申请记录。
     */
    @GetMapping("/refunds")
    public Result<List<RefundRequest>> listRefunds(@RequestAttribute Long userId) {
        return Result.success(orderService.listRefundsByUser(userId));
    }

    // 下单请求体
    @Data
    static class CreateOrderRequest {
        private Long shopId;
        private Long addressId;
        private List<OrderItem> items;
    }

    // 评价请求体
    @Data
    static class ReviewRequest {
        private Long productId;
        private Integer rating;
        private String content;
    }

    // 退款请求体
    @Data
    static class RefundApplyRequest {
        private String reason;
        private BigDecimal amount;
    }
}
