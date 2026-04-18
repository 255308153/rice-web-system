package com.rice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.ws.ChatSessionRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AIRecognitionMqService {
    private static final Logger log = LoggerFactory.getLogger(AIRecognitionMqService.class);

    public static final String TYPE_RICE = "RICE_TYPE";
    public static final String TYPE_DISEASE = "DISEASE";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_FORBIDDEN = "FORBIDDEN";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private YoloService yoloService;

    @Autowired
    private ChatService chatService;

    @Autowired(required = false)
    private ChatSessionRegistry chatSessionRegistry;

    @Value("${ai.recognition.queue-key:ai:recognition:queue}")
    private String queueKey;

    @Value("${ai.recognition.task-key-prefix:ai:recognition:task:}")
    private String taskKeyPrefix;

    @Value("${ai.recognition.task-ttl-hours:24}")
    private int taskTtlHours;

    @Value("${ai.recognition.worker-enabled:true}")
    private boolean workerEnabled;

    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "ai-recognition-mq-worker");
        worker.setDaemon(true);
        return worker;
    });

    private volatile boolean running;

    @PostConstruct
    public void startWorker() {
        if (!workerEnabled) {
            log.info("AI识别队列消费者已关闭 (ai.recognition.worker-enabled=false)");
            return;
        }
        running = true;
        workerExecutor.submit(this::consumeLoop);
        log.info("AI识别队列消费者已启动, queueKey={}", queueKey);
    }

    @PreDestroy
    public void stopWorker() {
        running = false;
        workerExecutor.shutdownNow();
    }

    public Map<String, Object> submitTask(Long userId, MultipartFile image, String recognitionType) throws Exception {
        String imageUrl = fileUploadService.upload(image);
        return submitTaskByImageUrl(userId, imageUrl, recognitionType);
    }

    public Map<String, Object> submitTaskByImageUrl(Long userId, String imageUrl, String recognitionType) throws Exception {
        String normalizedType = normalizeType(recognitionType);
        if (userId == null) {
            throw new IllegalArgumentException("用户标识不能为空");
        }
        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("图片地址不能为空");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("userId", userId);
        payload.put("recognitionType", normalizedType);
        payload.put("imageUrl", imageUrl);
        payload.put("createdAt", now.toString());

        Map<String, Object> status = new LinkedHashMap<>(payload);
        status.put("status", STATUS_PENDING);
        status.put("message", "任务已提交，等待识别");
        status.put("updatedAt", now.toString());

        saveTaskStatus(taskId, status);
        stringRedisTemplate.opsForList().rightPush(queueKey, objectMapper.writeValueAsString(payload));
        return status;
    }

    public Map<String, Object> queryTask(String taskId, Long userId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        Map<String, Object> status = readTaskStatus(taskId);
        if (status == null) {
            return null;
        }
        Long ownerId = toLong(status.get("userId"));
        if (ownerId == null || !ownerId.equals(userId)) {
            Map<String, Object> forbidden = new LinkedHashMap<>();
            forbidden.put("taskId", taskId);
            forbidden.put("status", STATUS_FORBIDDEN);
            forbidden.put("message", "无权限查看该任务");
            return forbidden;
        }
        return status;
    }

    public int queueSize() {
        Long size = stringRedisTemplate.opsForList().size(queueKey);
        return size == null ? 0 : size.intValue();
    }

    private void consumeLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                String raw = stringRedisTemplate.opsForList().leftPop(queueKey, Duration.ofSeconds(2));
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                consumeOne(raw);
            } catch (Exception e) {
                log.error("AI识别队列消费异常: {}", e.getMessage(), e);
            }
        }
    }

    private void consumeOne(String raw) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            log.warn("AI识别任务反序列化失败: {}", e.getMessage());
            return;
        }

        String taskId = asText(payload.get("taskId"));
        Long userId = toLong(payload.get("userId"));
        String imageUrl = asText(payload.get("imageUrl"));
        String recognitionType;
        try {
            recognitionType = normalizeType(asText(payload.get("recognitionType")));
        } catch (Exception e) {
            updateTaskStatus(taskId, STATUS_FAILED, "识别类型无效", null, "invalid-recognition-type");
            return;
        }

        if (!StringUtils.hasText(taskId) || userId == null || !StringUtils.hasText(imageUrl)) {
            updateTaskStatus(taskId, STATUS_FAILED, "任务参数缺失", null, "invalid-task-payload");
            return;
        }

        updateTaskStatus(taskId, STATUS_PROCESSING, "任务处理中", null, null);
        try {
            Map<String, Object> result = TYPE_RICE.equals(recognitionType)
                    ? yoloService.recognizeRiceTypeByImageUrl(userId, imageUrl)
                    : yoloService.recognizeDiseaseByImageUrl(userId, imageUrl);
            result.put("assistantReply", chatService.generateRecognitionAdvice(userId, result));
            updateTaskStatus(taskId, STATUS_SUCCESS, "识别完成", result, null);
            pushTaskEvent(userId, taskId, STATUS_SUCCESS, result, null);
        } catch (Exception e) {
            String error = rootMessage(e);
            updateTaskStatus(taskId, STATUS_FAILED, "识别失败: " + error, null, error);
            pushTaskEvent(userId, taskId, STATUS_FAILED, null, error);
        }
    }

    private void updateTaskStatus(String taskId,
                                  String status,
                                  String message,
                                  Map<String, Object> result,
                                  String error) {
        if (!StringUtils.hasText(taskId)) {
            return;
        }
        Map<String, Object> current = readTaskStatus(taskId);
        if (current == null) {
            current = new LinkedHashMap<>();
            current.put("taskId", taskId);
        }
        current.put("status", status);
        current.put("message", message);
        current.put("updatedAt", LocalDateTime.now().toString());
        if (result != null) {
            current.put("result", result);
        }
        if (error != null) {
            current.put("error", error);
        }
        saveTaskStatus(taskId, current);
    }

    private void saveTaskStatus(String taskId, Map<String, Object> taskStatus) {
        if (!StringUtils.hasText(taskId) || taskStatus == null) {
            return;
        }
        try {
            String key = taskKey(taskId);
            String content = objectMapper.writeValueAsString(taskStatus);
            stringRedisTemplate.opsForValue().set(key, content, Duration.ofHours(Math.max(1, taskTtlHours)));
        } catch (Exception e) {
            throw new RuntimeException("保存任务状态失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> readTaskStatus(String taskId) {
        try {
            String content = stringRedisTemplate.opsForValue().get(taskKey(taskId));
            if (!StringUtils.hasText(content)) {
                return null;
            }
            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeType(String recognitionType) {
        String type = asText(recognitionType).toUpperCase();
        if (TYPE_RICE.equals(type) || TYPE_DISEASE.equals(type)) {
            return type;
        }
        throw new IllegalArgumentException("不支持的识别类型: " + recognitionType);
    }

    private void pushTaskEvent(Long userId,
                               String taskId,
                               String status,
                               Map<String, Object> result,
                               String error) {
        if (chatSessionRegistry == null || userId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "AI_RECOGNITION_TASK");
            payload.put("taskId", taskId);
            payload.put("status", status);
            payload.put("result", result);
            payload.put("error", error);
            payload.put("timestamp", LocalDateTime.now().toString());
            chatSessionRegistry.sendToUser(userId, objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            // 推送失败不影响任务主流程
        }
    }

    private String taskKey(String taskId) {
        return taskKeyPrefix + taskId;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null ? "unknown" : current.getMessage();
    }
}
