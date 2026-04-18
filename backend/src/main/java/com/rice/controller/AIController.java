package com.rice.controller;

import com.rice.common.Result;
import com.rice.entity.AIChat;
import com.rice.entity.AIRecognition;
import com.rice.service.AIRecognitionMqService;
import com.rice.service.ChatService;
import com.rice.service.YoloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai")
public class AIController {
    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024;

    @Autowired
    private YoloService yoloService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AIRecognitionMqService recognitionMqService;

    @PostMapping("/recognize/rice-type")
    public CompletableFuture<Result<Map<String, Object>>> recognizeRiceType(
            @RequestParam("image") MultipartFile image,
            @RequestAttribute("userId") Long userId) {
        String validationError = validateImage(image);
        if (validationError != null) {
            return CompletableFuture.completedFuture(Result.error(validationError));
        }
        return yoloService.recognizeRiceTypeAsync(userId, image)
                .thenApply(result -> {
                    result.put("assistantReply", chatService.generateRecognitionAdvice(userId, result));
                    return Result.success(result);
                })
                .exceptionally(ex -> Result.error("大米品种识别失败：" + rootMessage(ex)));
    }

    @PostMapping("/recognize/disease")
    public CompletableFuture<Result<Map<String, Object>>> recognizeDisease(
            @RequestParam("image") MultipartFile image,
            @RequestAttribute("userId") Long userId) {
        String validationError = validateImage(image);
        if (validationError != null) {
            return CompletableFuture.completedFuture(Result.error(validationError));
        }
        return yoloService.recognizeDiseaseAsync(userId, image)
                .thenApply(result -> {
                    result.put("assistantReply", chatService.generateRecognitionAdvice(userId, result));
                    return Result.success(result);
                })
                .exceptionally(ex -> Result.error("水稻病害识别失败：" + rootMessage(ex)));
    }

    @PostMapping("/recognize/rice-type/task")
    public Result<Map<String, Object>> submitRiceTypeTask(
            @RequestParam("image") MultipartFile image,
            @RequestAttribute("userId") Long userId) {
        String validationError = validateImage(image);
        if (validationError != null) {
            return Result.error(validationError);
        }
        try {
            Map<String, Object> task = recognitionMqService.submitTask(userId, image, AIRecognitionMqService.TYPE_RICE);
            return Result.success(task);
        } catch (Exception e) {
            return Result.error("提交识别任务失败：" + e.getMessage());
        }
    }

    @PostMapping("/recognize/disease/task")
    public Result<Map<String, Object>> submitDiseaseTask(
            @RequestParam("image") MultipartFile image,
            @RequestAttribute("userId") Long userId) {
        String validationError = validateImage(image);
        if (validationError != null) {
            return Result.error(validationError);
        }
        try {
            Map<String, Object> task = recognitionMqService.submitTask(userId, image, AIRecognitionMqService.TYPE_DISEASE);
            return Result.success(task);
        } catch (Exception e) {
            return Result.error("提交识别任务失败：" + e.getMessage());
        }
    }

    @GetMapping("/recognize/task/{taskId}")
    public Result<Map<String, Object>> queryTask(
            @PathVariable String taskId,
            @RequestAttribute("userId") Long userId) {
        Map<String, Object> task = recognitionMqService.queryTask(taskId, userId);
        if (task == null) {
            return Result.error(404, "任务不存在或已过期");
        }
        if ("FORBIDDEN".equals(String.valueOf(task.get("status")))) {
            return Result.error(403, "无权限查看该任务");
        }
        return Result.success(task);
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
        if (image.getSize() > MAX_IMAGE_SIZE) {
            return "图片大小不能超过20MB";
        }
        String contentType = image.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            return "只支持jpg/png格式";
        }
        String originalFilename = image.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "文件名不能为空";
        }
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg") && !lowerName.endsWith(".png")) {
            return "文件扩展名仅支持jpg/jpeg/png";
        }
        try (InputStream input = image.getInputStream()) {
            byte[] header = new byte[8];
            int read = input.read(header);
            if (read < 3) {
                return "图片内容无效";
            }
            boolean isJpeg = read >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
            boolean isPng = read >= 8 && Arrays.equals(header, new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            });
            if (!isJpeg && !isPng) {
                return "图片二进制格式校验失败";
            }
        } catch (Exception e) {
            return "读取图片内容失败";
        }
        return null;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null ? "unknown" : current.getMessage();
    }
}
