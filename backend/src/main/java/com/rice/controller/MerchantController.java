package com.rice.controller;

import com.rice.common.Result;
import com.rice.dto.MerchantOrderStatsDTO;
import com.rice.dto.HotProductDTO;
import com.rice.entity.Order;
import com.rice.entity.Product;
import com.rice.entity.RefundRequest;
import com.rice.service.FileUploadService;
import com.rice.service.MerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/products")
    public Result<Void> addProduct(@RequestBody Product product) {
        merchantService.addProduct(product);
        return Result.success();
    }

    @PutMapping("/products/{id}")
    public Result<Void> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        merchantService.updateProduct(product);
        return Result.success();
    }

    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        merchantService.deleteProduct(id);
        return Result.success();
    }

    @PostMapping("/products/upload-image")
    public Result<Map<String, String>> uploadProductImage(@RequestParam("image") MultipartFile image) {
        try {
            if (image == null || image.isEmpty()) {
                return Result.error("请选择图片");
            }
            if (image.getSize() > 5 * 1024 * 1024) {
                return Result.error("图片大小不能超过5MB");
            }
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("仅支持图片文件");
            }

            String imageUrl = fileUploadService.upload(image);
            Map<String, String> data = new HashMap<>();
            data.put("url", imageUrl);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("上传失败：" + e.getMessage());
        }
    }

    @GetMapping("/orders")
    public Result<List<Order>> getMerchantOrders(@RequestAttribute("userId") Long merchantId,
                                                 @RequestParam(required = false) Long shopId,
                                                 @RequestParam(required = false) Integer status) {
        return Result.success(merchantService.getMerchantOrders(merchantId, shopId, status));
    }

    @GetMapping("/orders/stats")
    public Result<MerchantOrderStatsDTO> getOrderStats(@RequestAttribute("userId") Long merchantId) {
        return Result.success(merchantService.getOrderStats(merchantId));
    }

    @GetMapping("/orders/top-products")
    public Result<List<HotProductDTO>> getTopProducts(@RequestAttribute("userId") Long merchantId,
                                                      @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(merchantService.getTopProducts(merchantId, limit == null ? 5 : limit));
    }

    @GetMapping("/assistant/summary")
    public Result<Map<String, Object>> getAssistantSummary(@RequestAttribute("userId") Long merchantId) {
        return Result.success(merchantService.getSalesAssistantSummary(merchantId));
    }

    @PostMapping("/assistant/chat")
    public Result<String> assistantChat(@RequestAttribute("userId") Long merchantId,
                                        @RequestBody Map<String, String> params) {
        String message = params.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }
        return Result.success(merchantService.chatAssistant(merchantId, message.trim()));
    }

    @PostMapping("/orders/{id}/ship")
    public Result<Void> shipOrder(@RequestAttribute("userId") Long merchantId,
                                  @PathVariable Long id,
                                  @RequestBody Map<String, String> params) {
        merchantService.shipOrder(merchantId, id, params.get("company"), params.get("trackingNumber"));
        return Result.success();
    }

    @GetMapping("/refunds")
    public Result<List<RefundRequest>> getMerchantRefunds(@RequestAttribute("userId") Long merchantId,
                                                          @RequestParam(required = false) Integer status) {
        return Result.success(merchantService.getMerchantRefunds(merchantId, status));
    }

    @PostMapping("/refunds/{id}/process")
    public Result<Void> processRefund(@RequestAttribute("userId") Long merchantId,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> params) {
        Integer status = params.get("status") instanceof Number ? ((Number) params.get("status")).intValue() : null;
        String merchantRemark = params.get("merchantRemark") == null ? null : String.valueOf(params.get("merchantRemark"));
        merchantService.processRefund(merchantId, id, status, merchantRemark);
        return Result.success();
    }
}
