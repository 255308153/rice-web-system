package com.rice.controller;

import com.rice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @PostMapping
    public Result<Order> create(@RequestBody CreateOrderRequest req, @RequestAttribute Long userId) {
        return Result.success(orderService.create(userId, req.getShopId(), req.getAddressId(), req.getItems()));
    }

    @GetMapping
    public Result<List<Order>> list(@RequestAttribute Long userId) {
        return Result.success(orderService.listByUser(userId));
    }

    @GetMapping("/page")
    public Result<Page<Order>> page(@RequestAttribute Long userId,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "8") int size,
                                    @RequestParam(defaultValue = "-1") Integer status) {
        Integer queryStatus = status != null && status >= 0 ? status : null;
        return Result.success(orderService.pageByUser(userId, page, size, queryStatus));
    }

    @PutMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        orderService.pay(id);
        return Result.success();
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        orderService.confirm(id);
        return Result.success();
    }

    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @RequestBody ReviewRequest req, @RequestAttribute Long userId) {
        orderService.review(id, userId, req.getProductId(), req.getRating(), req.getContent());
        return Result.success();
    }

    @PostMapping("/{id}/refund")
    public Result<Void> applyRefund(@PathVariable Long id,
                                    @RequestBody RefundApplyRequest req,
                                    @RequestAttribute Long userId) {
        orderService.applyRefund(id, userId, req.getReason(), req.getAmount());
        return Result.success();
    }

    @GetMapping("/refunds")
    public Result<List<RefundRequest>> listRefunds(@RequestAttribute Long userId) {
        return Result.success(orderService.listRefundsByUser(userId));
    }

    @Data
    static class CreateOrderRequest {
        private Long shopId;
        private Long addressId;
        private List<OrderItem> items;
    }

    @Data
    static class ReviewRequest {
        private Long productId;
        private Integer rating;
        private String content;
    }

    @Data
    static class RefundApplyRequest {
        private String reason;
        private BigDecimal amount;
    }
}
