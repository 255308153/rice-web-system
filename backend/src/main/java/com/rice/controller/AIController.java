package com.rice.controller;

import com.rice.common.Result;
import com.rice.entity.AIChat;
import com.rice.entity.AIRecognition;
import com.rice.service.ChatService;
import com.rice.service.YoloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Autowired
    private YoloService yoloService;

    @Autowired
    private ChatService chatService;

    @PostMapping("/recognize/rice-type")
    public Result<Map<String, Object>> recognizeRiceType(
            @RequestParam("image") MultipartFile image,
            @RequestAttribute("userId") Long userId) {
        String validationError = validateImage(image);
        if (validationError != null) {
            return Result.error(validationError);
        }
        try {
            Map<String, Object> result = yoloService.recognizeRiceType(userId, image);
            result.put("assistantReply", chatService.generateRecognitionAdvice(userId, result));
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("大米品种识别失败：" + e.getMessage());
        }
    }

    @PostMapping("/recognize/disease")
    public Result<Map<String, Object>> recognizeDisease(
            @RequestParam("image") MultipartFile image,
            @RequestAttribute("userId") Long userId) {
        String validationError = validateImage(image);
        if (validationError != null) {
            return Result.error(validationError);
        }
        try {
            Map<String, Object> result = yoloService.recognizeDisease(userId, image);
            result.put("assistantReply", chatService.generateRecognitionAdvice(userId, result));
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("水稻病害识别失败：" + e.getMessage());
        }
    }

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, String> request, @RequestAttribute("userId") Long userId) {
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }
        try {
            String answer = chatService.chat(userId, message);
            return Result.success(answer);
        } catch (Exception e) {
            return Result.error("AI问答失败：" + e.getMessage());
        }
    }

    @GetMapping("/recognition/history")
    public Result<List<AIRecognition>> recognitionHistory(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        List<AIRecognition> history = yoloService.getHistory(userId, limit);
        return Result.success(history);
    }

    @GetMapping("/chat/history")
    public Result<List<AIChat>> chatHistory(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        List<AIChat> history = chatService.getHistory(userId, limit);
        return Result.success(history);
    }

    @GetMapping("/chat/health")
    public Result<Map<String, Object>> chatHealth() {
        return Result.success(chatService.healthCheck());
    }

    @GetMapping("/recognition/health")
    public Result<Map<String, Object>> recognitionHealth() {
        return Result.success(yoloService.healthCheck());
    }

    private String validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return "请上传图片";
        }
        if (image.getSize() > 5 * 1024 * 1024) {
            return "图片大小不能超过5MB";
        }
        String contentType = image.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            return "只支持jpg/png格式";
        }
        return null;
    }
}
