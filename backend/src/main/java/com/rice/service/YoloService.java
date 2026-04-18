package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.entity.AIRecognition;
import com.rice.entity.SystemConfig;
import com.rice.mapper.AIRecognitionMapper;
import com.rice.mapper.SystemConfigMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class YoloService {
    private static final String RECOGNITION_RICE_TYPE = "RICE_TYPE";
    private static final String RECOGNITION_DISEASE = "DISEASE";

    @Autowired
    private AIRecognitionMapper recognitionMapper;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Value("${ai.yolo-url:http://localhost:8001/predict}")
    private String defaultYoloServiceUrl;

    @Value("${ai.yolo-rice-type-url:${ai.yolo-url:http://localhost:8001/predict}}")
    private String defaultRiceTypeServiceUrl;

    @Value("${ai.yolo-disease-url:${ai.yolo-url:http://localhost:8001/predict}}")
    private String defaultDiseaseServiceUrl;

    @Value("${ai.yolo-timeout-seconds:30}")
    private int yoloTimeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Map<String, Object> recognizeRiceType(Long userId, MultipartFile image) throws Exception {
        return waitResult(recognizeRiceTypeAsync(userId, image));
    }

    public Map<String, Object> recognizeDisease(Long userId, MultipartFile image) throws Exception {
        return waitResult(recognizeDiseaseAsync(userId, image));
    }

    public CompletableFuture<Map<String, Object>> recognizeRiceTypeAsync(Long userId, MultipartFile image) {
        return recognizeByTypeAsync(userId, image, RECOGNITION_RICE_TYPE);
    }

    public CompletableFuture<Map<String, Object>> recognizeDiseaseAsync(Long userId, MultipartFile image) {
        return recognizeByTypeAsync(userId, image, RECOGNITION_DISEASE);
    }

    public Map<String, Object> recognizeRiceTypeByImageUrl(Long userId, String imageUrl) throws Exception {
        return recognizeByImageUrl(userId, imageUrl, RECOGNITION_RICE_TYPE);
    }

    public Map<String, Object> recognizeDiseaseByImageUrl(Long userId, String imageUrl) throws Exception {
        return recognizeByImageUrl(userId, imageUrl, RECOGNITION_DISEASE);
    }

    private CompletableFuture<Map<String, Object>> recognizeByTypeAsync(Long userId, MultipartFile image, String recognitionType) {
        final String imageUrl;
        try {
            imageUrl = fileUploadService.upload(image);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        String serviceUrl = resolveYoloServiceUrl(recognitionType);
        return callPredictServiceAsync(serviceUrl, image, recognitionType)
                .exceptionally(ex -> buildFallbackResult(unwrapException(ex), serviceUrl))
                .thenApply(fullResult -> {
                    normalizeResult(fullResult, serviceUrl);
                    Map<String, Object> result = buildScopedResult(fullResult, recognitionType);
                    result.put("imageUrl", imageUrl);
                    result.put("recognitionType", recognitionType);
                    try {
                        saveRecognitionRecord(userId, imageUrl, result);
                    } catch (Exception ignored) {
                    }
                    return result;
                });
    }

    private Map<String, Object> recognizeByType(Long userId, MultipartFile image, String recognitionType) throws Exception {
        String imageUrl = fileUploadService.upload(image);

        Map<String, Object> fullResult;
        String serviceUrl = resolveYoloServiceUrl(recognitionType);
        try {
            fullResult = callPredictService(serviceUrl, image, recognitionType);
            if (fullResult == null || fullResult.isEmpty()) {
                throw new RuntimeException("识别服务返回为空");
            }
        } catch (Exception e) {
            fullResult = buildFallbackResult(e, serviceUrl);
        }

        normalizeResult(fullResult, serviceUrl);
        Map<String, Object> result = buildScopedResult(fullResult, recognitionType);
        result.put("imageUrl", imageUrl);
        result.put("recognitionType", recognitionType);

        saveRecognitionRecord(userId, imageUrl, result);
        return result;
    }

    private Map<String, Object> recognizeByImageUrl(Long userId, String imageUrl, String recognitionType) throws Exception {
        byte[] imageBytes = fileUploadService.readByPublicUrl(imageUrl);
        String filename = extractFilename(imageUrl);

        Map<String, Object> fullResult;
        String serviceUrl = resolveYoloServiceUrl(recognitionType);
        try {
            fullResult = callPredictService(serviceUrl, imageBytes, filename, recognitionType);
            if (fullResult == null || fullResult.isEmpty()) {
                throw new RuntimeException("识别服务返回为空");
            }
        } catch (Exception e) {
            fullResult = buildFallbackResult(e, serviceUrl);
        }

        normalizeResult(fullResult, serviceUrl);
        Map<String, Object> result = buildScopedResult(fullResult, recognitionType);
        result.put("imageUrl", imageUrl);
        result.put("recognitionType", recognitionType);

        saveRecognitionRecord(userId, imageUrl, result);
        return result;
    }

    public List<AIRecognition> getHistory(Long userId, int limit) {
        return recognitionMapper.findByUserId(userId, limit);
    }

    public Map<String, Object> healthCheck() {
        Map<String, Object> result = new LinkedHashMap<>();
        String riceTypeServiceUrl = resolveYoloServiceUrl(RECOGNITION_RICE_TYPE);
        String diseaseServiceUrl = resolveYoloServiceUrl(RECOGNITION_DISEASE);

        Map<String, Object> riceTypeHealth = checkServiceHealth(riceTypeServiceUrl);
        Map<String, Object> diseaseHealth = checkServiceHealth(diseaseServiceUrl);
        boolean riceTypeAvailable = Boolean.TRUE.equals(riceTypeHealth.get("available"));
        boolean diseaseAvailable = Boolean.TRUE.equals(diseaseHealth.get("available"));

        result.put("available", riceTypeAvailable || diseaseAvailable);
        if (riceTypeAvailable && diseaseAvailable) {
            String riceProvider = asText(riceTypeHealth.get("provider"), "remote");
            String diseaseProvider = asText(diseaseHealth.get("provider"), "remote");
            result.put("provider", riceProvider.equals(diseaseProvider) ? riceProvider : "mixed");
        } else if (riceTypeAvailable) {
            result.put("provider", riceTypeHealth.getOrDefault("provider", "remote"));
        } else if (diseaseAvailable) {
            result.put("provider", diseaseHealth.getOrDefault("provider", "remote"));
        } else {
            result.put("provider", "fallback");
        }

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("riceType", riceTypeHealth);
        services.put("disease", diseaseHealth);
        result.put("services", services);
        result.put("riceTypeYoloServiceUrl", riceTypeServiceUrl);
        result.put("diseaseYoloServiceUrl", diseaseServiceUrl);
        result.put("yoloServiceUrl", riceTypeServiceUrl);
        return result;
    }

    private Map<String, Object> checkServiceHealth(String serviceUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yoloServiceUrl", serviceUrl);
        try {
            Map<String, Object> remote = callHealthService(serviceUrl);
            result.put("available", true);
            result.put("provider", remote.getOrDefault("provider", "remote"));
            result.put("details", remote);
        } catch (Exception e) {
            result.put("available", false);
            result.put("provider", "fallback");
            result.put("reason", e.getMessage());
        }
        return result;
    }

    private String resolveYoloServiceUrl(String recognitionType) {
        Map<String, Object> aiConfig = getAiConfig();
        if (RECOGNITION_RICE_TYPE.equals(recognitionType)) {
            return firstText(
                    aiConfig.get("riceTypeYoloUrl"),
                    aiConfig.get("yoloRiceTypeUrl"),
                    aiConfig.get("riceTypeModelUrl"),
                    aiConfig.get("yoloUrl"),
                    defaultRiceTypeServiceUrl,
                    defaultYoloServiceUrl
            );
        }
        return firstText(
                aiConfig.get("diseaseYoloUrl"),
                aiConfig.get("yoloDiseaseUrl"),
                aiConfig.get("diseaseModelUrl"),
                aiConfig.get("yoloUrl"),
                defaultDiseaseServiceUrl,
                defaultYoloServiceUrl
        );
    }

    @Cacheable(cacheNames = "ai:config", key = "'ai-config-map'")
    public Map<String, Object> getAiConfig() {
        try {
            SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, "ai")
                    .last("LIMIT 1"));
            if (config != null && StringUtils.hasText(config.getConfigValue())) {
                return objectMapper.readValue(config.getConfigValue(), Map.class);
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> callPredictService(String serviceUrl, MultipartFile image, String recognitionType) throws Exception {
        if (!StringUtils.hasText(serviceUrl)) {
            throw new RuntimeException("未配置识别服务地址");
        }
        return callPredictService(serviceUrl, image.getBytes(), image.getOriginalFilename(), recognitionType);
    }

    private Map<String, Object> callPredictService(String serviceUrl, byte[] imageBytes, String filename, String recognitionType) throws Exception {
        String predictUrl = normalizePredictUrl(serviceUrl);
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("imageBase64", imageBase64);
        payload.put("filename", filename);
        payload.put("recognitionType", recognitionType);
        String body = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(predictUrl))
                .timeout(Duration.ofSeconds(Math.max(5, yoloTimeoutSeconds)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parsePredictResponse(response);
    }

    private CompletableFuture<Map<String, Object>> callPredictServiceAsync(String serviceUrl, MultipartFile image, String recognitionType) {
        if (!StringUtils.hasText(serviceUrl)) {
            return CompletableFuture.failedFuture(new RuntimeException("未配置识别服务地址"));
        }
        try {
            return callPredictServiceAsync(serviceUrl, image.getBytes(), image.getOriginalFilename(), recognitionType);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<Map<String, Object>> callPredictServiceAsync(String serviceUrl, byte[] imageBytes, String filename, String recognitionType) {
        if (!StringUtils.hasText(serviceUrl)) {
            return CompletableFuture.failedFuture(new RuntimeException("未配置识别服务地址"));
        }
        try {
            String predictUrl = normalizePredictUrl(serviceUrl);
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> payload = new HashMap<>();
            payload.put("imageBase64", imageBase64);
            payload.put("filename", filename);
            payload.put("recognitionType", recognitionType);
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(predictUrl))
                    .timeout(Duration.ofSeconds(Math.max(5, yoloTimeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> {
                        try {
                            return parsePredictResponse(response);
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Map<String, Object> parsePredictResponse(HttpResponse<String> response) throws Exception {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String bodySnippet = asText(response.body(), "");
            if (bodySnippet.length() > 220) {
                bodySnippet = bodySnippet.substring(0, 220) + "...";
            }
            throw new RuntimeException("识别服务异常(" + response.statusCode() + ")" +
                    (StringUtils.hasText(bodySnippet) ? (": " + bodySnippet) : ""));
        }
        Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
        if (parsed.containsKey("data") && parsed.get("data") instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return parsed;
    }

    private Map<String, Object> callHealthService(String serviceUrl) throws Exception {
        String healthUrl = normalizeHealthUrl(serviceUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .timeout(Duration.ofSeconds(Math.max(3, yoloTimeoutSeconds / 2)))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("识别健康检查异常(" + response.statusCode() + ")");
        }
        return objectMapper.readValue(response.body(), Map.class);
    }

    private String normalizePredictUrl(String rawUrl) {
        String url = rawUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new RuntimeException("识别服务地址格式不正确: " + rawUrl);
        }
        if (url.endsWith("/predict")) {
            return url;
        }
        if (url.endsWith("/")) {
            return url + "predict";
        }
        return url + "/predict";
    }

    private String normalizeHealthUrl(String rawUrl) {
        String url = rawUrl.trim();
        if (url.endsWith("/predict")) {
            return url.substring(0, url.length() - "/predict".length()) + "/health";
        }
        if (url.endsWith("/health")) {
            return url;
        }
        if (url.endsWith("/")) {
            return url + "health";
        }
        return url + "/health";
    }

    private Map<String, Object> buildScopedResult(Map<String, Object> fullResult, String recognitionType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", fullResult.getOrDefault("provider", "remote"));
        result.put("modelVersion", fullResult.getOrDefault("modelVersion", "v1"));
        result.put("yoloServiceUrl", fullResult.get("yoloServiceUrl"));
        if (fullResult.containsKey("reason")) {
            result.put("reason", fullResult.get("reason"));
        }

        if (RECOGNITION_RICE_TYPE.equals(recognitionType)) {
            String riceType = asText(fullResult.get("riceType"), asText(fullResult.get("type"), "未知品种"));
            BigDecimal riceConfidence = parseConfidence(fullResult.get("riceConfidence"));
            result.put("type", riceType);
            result.put("riceType", riceType);
            result.put("riceConfidence", riceConfidence);
            result.put("confidence", riceConfidence);
            result.put("suggestions", buildRiceTypeSuggestions(riceType));
            return result;
        }

        String diseaseName = asText(fullResult.get("diseaseName"), "未识别病害");
        BigDecimal diseaseConfidence = parseConfidence(fullResult.get("diseaseConfidence"));
        result.put("diseaseName", diseaseName);
        result.put("diseaseConfidence", diseaseConfidence);
        result.put("confidence", diseaseConfidence);
        result.put("suggestions", buildDiseaseSuggestions(diseaseName));
        return result;
    }

    private void normalizeResult(Map<String, Object> result, String serviceUrl) {
        String riceType = asText(result.get("riceType"), asText(result.get("type"), "未知品种"));
        String diseaseName = asText(result.get("diseaseName"), "未识别病害");
        BigDecimal riceConfidence = parseConfidence(result.get("riceConfidence"));
        BigDecimal diseaseConfidence = parseConfidence(result.get("diseaseConfidence"));
        BigDecimal confidence = parseConfidence(result.get("confidence"));
        if (confidence.compareTo(BigDecimal.ZERO) <= 0) {
            confidence = riceConfidence.max(diseaseConfidence);
        }

        if (!result.containsKey("suggestions") || !StringUtils.hasText(String.valueOf(result.get("suggestions")))) {
            result.put("suggestions", buildSuggestions(diseaseName, riceType));
        }

        result.put("type", riceType);
        result.put("riceType", riceType);
        result.put("diseaseName", diseaseName);
        result.put("riceConfidence", riceConfidence);
        result.put("diseaseConfidence", diseaseConfidence);
        result.put("confidence", confidence);
        result.put("yoloServiceUrl", serviceUrl);
        result.putIfAbsent("provider", "remote");
        result.putIfAbsent("modelVersion", "v1");
    }

    private void saveRecognitionRecord(Long userId, String imageUrl, Map<String, Object> result) throws Exception {
        AIRecognition recognition = new AIRecognition();
        recognition.setUserId(userId);
        recognition.setImageUrl(imageUrl);
        recognition.setResult(objectMapper.writeValueAsString(result));
        recognition.setConfidence(parseConfidence(result.get("confidence")));
        recognitionMapper.insert(recognition);
    }

    private Map<String, Object> buildFallbackResult(Exception e, String serviceUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "籼米");
        result.put("riceType", "籼米");
        result.put("diseaseName", "未识别病害");
        result.put("riceConfidence", new BigDecimal("0.80"));
        result.put("diseaseConfidence", new BigDecimal("0.60"));
        result.put("confidence", new BigDecimal("0.80"));
        result.put("suggestions", "当前为本地应急识别结果，请优先进行田间复核，必要时联系农技人员现场诊断。");
        result.put("provider", "mock-fallback");
        result.put("modelVersion", "fallback");
        result.put("reason", e == null ? "unknown" : String.valueOf(e.getMessage()));
        result.put("yoloServiceUrl", serviceUrl);
        return result;
    }

    private String buildRiceTypeSuggestions(String riceType) {
        if (StringUtils.hasText(riceType)) {
            return "识别品种为" + riceType + "，建议结合当地农技站推荐方案进行精准施肥与田间巡查。";
        }
        return "未识别出明确品种，请上传更清晰的大米颗粒近景图再试。";
    }

    private String buildDiseaseSuggestions(String diseaseName) {
        String name = asText(diseaseName, "未识别病害");
        if (name.contains("blast") || name.contains("稻瘟")) {
            return "疑似稻瘟病：建议及时清除重病叶片，保持通风，优先使用登记药剂并在5-7天复查。";
        }
        if (name.contains("blight") || name.contains("白叶枯")) {
            return "疑似白叶枯病：建议控氮稳钾、清沟排水，发病初期使用对症药剂并避免大水漫灌。";
        }
        if (name.contains("tungro") || name.contains("东格鲁")) {
            return "疑似东格鲁病：建议及时拔除病株，做好虫媒防控，并加强田块隔离管理。";
        }
        return "暂未识别明确病害，建议结合田间症状继续巡查，必要时联系农技人员复核。";
    }

    private String buildSuggestions(String diseaseName, String riceType) {
        if (StringUtils.hasText(diseaseName)) {
            String name = diseaseName.trim();
            if (name.contains("blast") || name.contains("稻瘟")) {
                return "疑似稻瘟病：建议及时清除重病叶片，保持通风，优先使用登记药剂并在5-7天复查。";
            }
            if (name.contains("blight") || name.contains("白叶枯")) {
                return "疑似白叶枯病：建议控氮稳钾、清沟排水，发病初期使用对症药剂并避免大水漫灌。";
            }
            if (name.contains("tungro") || name.contains("东格鲁")) {
                return "疑似东格鲁病：建议及时拔除病株，做好虫媒防控，并加强田块隔离管理。";
            }
        }
        return StringUtils.hasText(riceType)
                ? "识别品种为" + riceType + "，建议结合当地农技站推荐管理方案进行精准施肥与病害巡查。"
                : "请结合现场症状进一步复核，必要时上传更清晰的叶片近景图片。";
    }

    private String asText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private BigDecimal parseConfidence(Object value) {
        try {
            if (value instanceof Number num) {
                return new BigDecimal(String.valueOf(num.doubleValue())).max(BigDecimal.ZERO);
            }
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return new BigDecimal(String.valueOf(value)).max(BigDecimal.ZERO);
            }
        } catch (Exception ignored) {
        }
        return BigDecimal.ZERO;
    }

    private String firstText(Object... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private Map<String, Object> waitResult(CompletableFuture<Map<String, Object>> future) throws Exception {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = unwrapException(e);
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause.getMessage(), cause);
        }
    }

    private Exception unwrapException(Throwable e) {
        Throwable current = e;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof Exception ex ? ex : new RuntimeException(current == null ? "unknown" : current.getMessage(), current);
    }

    private String extractFilename(String imageUrl) {
        String fallback = "recognition-image.jpg";
        if (!StringUtils.hasText(imageUrl)) {
            return fallback;
        }
        int index = imageUrl.lastIndexOf('/');
        if (index < 0 || index >= imageUrl.length() - 1) {
            return fallback;
        }
        String filename = imageUrl.substring(index + 1).trim();
        return StringUtils.hasText(filename) ? filename : fallback;
    }
}
