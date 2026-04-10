package com.rice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.common.Result;
import com.rice.dto.HotProductDTO;
import com.rice.dto.MonitorOverviewDTO;
import com.rice.entity.Certification;
import com.rice.entity.Comment;
import com.rice.entity.Order;
import com.rice.entity.Post;
import com.rice.entity.SystemNotification;
import com.rice.entity.User;
import com.rice.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/users")
    public Result<Page<User>> getUserList(@RequestAttribute("userId") Long adminId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer status) {
        return Result.success(adminService.getUserList(adminId, page, size, role, keyword, status));
    }

    @GetMapping("/users/export")
    public Result<List<User>> exportUserList(@RequestAttribute("userId") Long adminId,
                                             @RequestParam(required = false) String role,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        return Result.success(adminService.exportUserList(adminId, role, keyword, status));
    }

    @GetMapping("/orders")
    public Result<Page<Order>> getOrderList(@RequestAttribute("userId") Long adminId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "100") int size) {
        return Result.success(adminService.getOrderList(adminId, page, size));
    }

    @GetMapping("/hot-products")
    public Result<List<HotProductDTO>> getHotProducts(@RequestAttribute("userId") Long adminId,
                                                      @RequestParam(defaultValue = "5") int limit) {
        return Result.success(adminService.getHotProducts(adminId, limit));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@RequestAttribute("userId") Long adminId,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Integer> params) {
        adminService.updateUserStatus(adminId, id, params.get("status"));
        return Result.success();
    }

    @PutMapping("/users/{id}/password")
    public Result<Void> resetUserPassword(@RequestAttribute("userId") Long adminId,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, String> params) {
        adminService.resetUserPassword(adminId, id, params.get("password"));
        return Result.success();
    }

    @GetMapping("/audits")
    public Result<Page<Certification>> getAuditList(@RequestAttribute("userId") Long adminId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(defaultValue = "0") Integer status,
                                                    @RequestParam(required = false) String role) {
        return Result.success(adminService.getAuditList(adminId, page, size, status, role));
    }

    @PutMapping("/audits/{id}")
    public Result<Void> auditCertification(@RequestAttribute("userId") Long adminId,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> params) {
        Integer status = (Integer) params.get("status");
        String remark = (String) params.get("remark");
        adminService.auditCertification(id, status, remark, adminId);
        return Result.success();
    }

    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@RequestAttribute("userId") Long adminId,
                                   @PathVariable Long id) {
        adminService.deletePost(id, adminId);
        return Result.success();
    }

    @GetMapping("/content/posts")
    public Result<Page<Post>> getPostAuditList(@RequestAttribute("userId") Long adminId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(defaultValue = "-1") Integer status,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(adminService.getPostAuditList(adminId, page, size, status, keyword));
    }

    @PutMapping("/content/posts/{id}/status")
    public Result<Void> updatePostStatus(@RequestAttribute("userId") Long adminId,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Object> params) {
        Integer status = params.get("status") instanceof Number ? ((Number) params.get("status")).intValue() : null;
        String remark = params.get("remark") == null ? null : String.valueOf(params.get("remark"));
        adminService.updatePostStatus(adminId, id, status, remark);
        return Result.success();
    }

    @GetMapping("/content/comments")
    public Result<Page<Comment>> getCommentAuditList(@RequestAttribute("userId") Long adminId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(defaultValue = "-1") Integer status,
                                                     @RequestParam(required = false) String keyword) {
        return Result.success(adminService.getCommentAuditList(adminId, page, size, status, keyword));
    }

    @PutMapping("/content/comments/{id}/status")
    public Result<Void> updateCommentStatus(@RequestAttribute("userId") Long adminId,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, Object> params) {
        Integer status = params.get("status") instanceof Number ? ((Number) params.get("status")).intValue() : null;
        String remark = params.get("remark") == null ? null : String.valueOf(params.get("remark"));
        adminService.updateCommentStatus(adminId, id, status, remark);
        return Result.success();
    }

    @GetMapping("/forum/categories")
    public Result<List<String>> getForumCategories(@RequestAttribute("userId") Long adminId) {
        return Result.success(adminService.getForumCategories(adminId));
    }

    @PutMapping("/forum/categories")
    public Result<Void> setForumCategories(@RequestAttribute("userId") Long adminId,
                                           @RequestBody Map<String, Object> params) {
        Object raw = params.get("categories");
        List<String> categories = new java.util.ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object item : (List<?>) raw) {
                if (item != null) {
                    categories.add(String.valueOf(item));
                }
            }
        }
        adminService.setForumCategories(adminId, categories);
        return Result.success();
    }

    @GetMapping("/notices")
    public Result<List<SystemNotification>> getNoticeList(@RequestAttribute("userId") Long userId,
                                                          @RequestParam(defaultValue = "100") int limit) {
        return Result.success(adminService.getNoticeList(userId, limit));
    }

    @PostMapping("/notices")
    public Result<Void> createNotice(@RequestAttribute("userId") Long adminId,
                                     @RequestBody Map<String, String> params) {
        adminService.createNotice(adminId, params.get("title"), params.get("content"), params.get("role"));
        return Result.success();
    }

    @DeleteMapping("/notices/{id}")
    public Result<Void> deleteNotice(@RequestAttribute("userId") Long adminId,
                                     @PathVariable Long id) {
        adminService.deleteNotice(adminId, id);
        return Result.success();
    }

    @PostMapping("/backup")
    public Result<Map<String, Object>> backupDatabase(@RequestAttribute("userId") Long adminId) {
        return Result.success(adminService.backupDatabase(adminId));
    }

    @GetMapping("/backups")
    public Result<List<Map<String, Object>>> getBackupFiles(@RequestAttribute("userId") Long adminId) {
        return Result.success(adminService.listBackupFiles(adminId));
    }

    @GetMapping("/monitor/overview")
    public Result<MonitorOverviewDTO> getMonitorOverview(@RequestAttribute("userId") Long adminId,
                                                         @RequestParam(defaultValue = "30") Integer windowDays,
                                                         @RequestParam(defaultValue = "3") Integer threshold) {
        return Result.success(adminService.getMonitorOverview(adminId, windowDays, threshold));
    }

    @GetMapping("/config/{key}")
    public Result<String> getConfig(@RequestAttribute("userId") Long adminId, @PathVariable String key) {
        return Result.success(adminService.getConfig(adminId, key));
    }

    @PutMapping("/config")
    public Result<Void> setConfig(@RequestAttribute("userId") Long adminId,
                                  @RequestBody Map<String, String> params) {
        adminService.setConfig(adminId, params.get("key"), params.get("value"), params.get("description"));
        return Result.success();
    }
}
