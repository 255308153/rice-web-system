package com.rice.controller;

import com.rice.common.Result;
import com.rice.entity.User;
import com.rice.service.FileUploadService;
import com.rice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/auth/register")
    public Result<Map<String, String>> register(@RequestBody Map<String, String> params) {
        String token = userService.register(
            params.get("username"),
            params.get("password"),
            params.get("phone")
        );
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        return Result.success(data);
    }

    @PostMapping("/auth/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> params) {
        String token = userService.login(params.get("username"), params.get("password"));
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        return Result.success(data);
    }

    @GetMapping("/user/info")
    public Result<User> getUserInfo(@RequestAttribute("userId") Long userId) {
        User user = userService.getUserById(userId);
        user.setPassword(null); // 不返回密码
        return Result.success(user);
    }

    @PutMapping("/user/info")
    public Result<Void> updateUserInfo(@RequestAttribute("userId") Long userId,
                                       @RequestBody User user) {
        userService.updateUser(userId, user);
        return Result.success();
    }

    @PostMapping("/user/upload-avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("image") MultipartFile image) {
        try {
            if (image == null || image.isEmpty()) {
                return Result.error("请选择头像图片");
            }
            if (image.getSize() > 5 * 1024 * 1024) {
                return Result.error("头像大小不能超过5MB");
            }
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("仅支持图片文件");
            }
            String avatarUrl = fileUploadService.upload(image);
            Map<String, String> data = new HashMap<>();
            data.put("url", avatarUrl);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("上传失败：" + e.getMessage());
        }
    }
}
