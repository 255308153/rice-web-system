package com.rice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.common.Result;
import com.rice.dto.ChatContactDTO;
import com.rice.entity.Conversation;
import com.rice.entity.Message;
import com.rice.service.FileUploadService;
import com.rice.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;
    private final FileUploadService fileUploadService;

    @PostMapping("/messages")
    public Result<Message> sendMessage(@RequestBody Map<String, Object> params,
                                        @RequestAttribute Long userId) {
        Object receiverObj = params.get("receiverId");
        Object contentObj = params.get("content");
        if (receiverObj == null || contentObj == null) {
            return Result.error("receiverId 和 content 不能为空");
        }
        Long receiverId = Long.valueOf(receiverObj.toString());
        String content = contentObj.toString();
        String type = params.getOrDefault("type", "TEXT").toString();
        return Result.success(messageService.sendMessage(userId, receiverId, content, type));
    }

    @PostMapping("/conversations/start")
    public Result<Conversation> startConversation(@RequestBody Map<String, Long> params,
                                                  @RequestAttribute Long userId) {
        Long receiverId = params.get("receiverId");
        return Result.success(messageService.startConversation(userId, receiverId));
    }

    @GetMapping("/conversations")
    public Result<Page<Conversation>> listConversations(@RequestAttribute Long userId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return Result.success(messageService.listConversations(userId, page, size));
    }

    @GetMapping("/conversations/preferences")
    public Result<Map<String, List<String>>> getConversationPreferences(@RequestAttribute Long userId) {
        return Result.success(messageService.getConversationPreferences(userId));
    }

    @PutMapping("/conversations/preferences")
    public Result<Void> saveConversationPreferences(@RequestAttribute Long userId,
                                                    @RequestBody Map<String, Object> params) {
        List<String> pinnedIds = toStringList(params.get("pinnedIds"));
        List<String> hiddenIds = toStringList(params.get("hiddenIds"));
        messageService.saveConversationPreferences(userId, pinnedIds, hiddenIds);
        return Result.success();
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<Page<Message>> listMessages(@PathVariable Long id,
                                               @RequestAttribute Long userId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return Result.success(messageService.listMessages(userId, id, page, size));
    }

    @GetMapping("/messages/contacts")
    public Result<List<ChatContactDTO>> listContacts(@RequestAttribute Long userId,
                                                     @RequestParam(required = false) String role,
                                                     @RequestParam(required = false) String keyword) {
        return Result.success(messageService.listContacts(userId, role, keyword));
    }

    @PostMapping("/messages/upload-image")
    public Result<Map<String, String>> uploadImage(@RequestParam("image") MultipartFile image) {
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

    private List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new java.util.ArrayList<>();
        }
        List<String> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            result.add(String.valueOf(item));
        }
        return result;
    }
}
