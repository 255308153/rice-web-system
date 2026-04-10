package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.entity.AIRecognition;
import com.rice.entity.AIChat;
import com.rice.entity.SystemConfig;
import com.rice.mapper.AIRecognitionMapper;
import com.rice.mapper.AIChatMapper;
import com.rice.mapper.SystemConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY_ROUNDS = 6;

    @Autowired
    private AIChatMapper chatMapper;

    @Autowired
    private AIRecognitionMapper recognitionMapper;

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Value("${bailian.api-key:}")
    private String bailianApiKey;

    @Value("${bailian.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String bailianBaseUrl;

    @Value("${bailian.model:qwen-plus}")
    private String bailianModel;

    @Value("${bailian.system-prompt:你是一个农业专家助手，请用中文给出简洁、可执行的建议。}")
    private String bailianSystemPrompt;

    @Value("${bailian.timeout-seconds:20}")
    private int bailianTimeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public String chat(Long userId, String message) {
        return chatInternal(userId, message, null, Collections.emptyList(), true, true, true);
    }

    public String generateRecognitionAdvice(Long userId, Map<String, Object> recognitionResult) {
        List<Map<String, String>> contextMessages = List.of(
                buildMessage("system", "以下是系统自动注入的最新识别结果，请直接基于结果给出建议，不要重复要求用户上传图片。\n"
                        + safeToJson(recognitionResult))
        );
        String prompt = "请根据这次识别结果，给出简洁、可执行的判断和处理建议。输出分为“识别判断、当前风险、建议操作”三部分。";
        String systemPrompt = "你是农业识别联动助手，需要把模型结果转成农户能立即执行的中文建议。";
        return chatInternal(userId, prompt, systemPrompt, contextMessages, true, false, true);
    }

    public String generateMerchantSalesSummary(Long userId, Map<String, Object> salesSummary) {
        List<Map<String, String>> contextMessages = List.of(
                buildMessage("system", "以下是当前店铺的结构化经营数据，请仅基于这些数据输出总结。\n" + safeToJson(salesSummary))
        );
        String prompt = "请输出一份店铺销售总结，分为“经营概览、问题诊断、增长建议”三部分，每部分 2 到 3 点，语言务实。";
        String systemPrompt = "你是电商经营分析助手，擅长根据交易数据总结店铺表现并给出销售建议。";
        return chatInternal(userId, prompt, systemPrompt, contextMessages, false, false, false);
    }

    public String chatForMerchantAssistant(Long userId, String message, Map<String, Object> salesSummary) {
        List<Map<String, String>> contextMessages = List.of(
                buildMessage("system", "以下是当前店铺的结构化经营数据与AI总结建议，请始终带着这些上下文回答。\n" + safeToJson(salesSummary))
        );
        String systemPrompt = "你是商户经营AI助手，需要结合店铺交易数据、商品表现和历史对话，持续提供销售分析与增长建议。";
        return chatInternal(userId, message, systemPrompt, contextMessages, true, false, true);
    }

    public Map<String, Object> reviewPostContent(Long userId, Map<String, Object> postPayload) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(buildMessage("system",
                "你是社区内容审核助手。请判断帖子是否存在明显违规风险，包括但不限于色情低俗、赌博诈骗、非法交易、引流广告、辱骂攻击、虚假夸大宣传等。"
                        + "只返回 JSON，不要输出任何额外文字。"
                        + "JSON 格式必须为：{\"violation\":true/false,\"reason\":\"...\",\"confidence\":\"HIGH|MEDIUM|LOW\"}。"
                        + "若未发现明显违规风险，reason 写“未发现明显违规风险”。"));
        messages.add(buildMessage("user", "请审核以下帖子：\n" + safeToJson(postPayload)));

        try {
            String raw = callBailian(messages);
            return parseAuditResponse(raw);
        } catch (Exception e) {
            log.warn("AI预审核不可用，切换本地规则审核: {}", e.getMessage());
            return buildLocalAuditResult(postPayload);
        }
    }

    public List<AIChat> getHistory(Long userId, int limit) {
        return chatMapper.findByUserId(userId, limit);
    }

    public Map<String, Object> healthCheck() {
        Map<String, Object> result = new LinkedHashMap<>();
        ApiKeyResolution keyResolution = resolveApiKeyWithSource();
        result.put("endpoint", bailianBaseUrl);
        result.put("model", bailianModel);
        result.put("keyConfigured", keyResolution.configured());
        result.put("keySource", keyResolution.source());

        if (!keyResolution.configured()) {
            result.put("available", false);
            result.put("reason", "未配置百炼 API Key");
            return result;
        }

        try {
            callBailian(List.of(buildMessage("user", "ping")));
            result.put("available", true);
            result.put("reason", "连通正常");
        } catch (Exception e) {
            String reason = e.getMessage() == null ? "未知错误" : e.getMessage();
            result.put("available", false);
            result.put("reason", reason);
        }
        return result;
    }

    private String chatInternal(Long userId,
                                String message,
                                String overrideSystemPrompt,
                                List<Map<String, String>> extraContext,
                                boolean persist,
                                boolean appendRecognitionContext,
                                boolean includeHistory) {
        String answer;
        try {
            List<Map<String, String>> messages = buildConversationMessages(
                    userId, message, overrideSystemPrompt, extraContext, appendRecognitionContext, includeHistory);
            answer = callBailian(messages);
        } catch (Exception e) {
            log.warn("百炼服务不可用，切换本地兜底问答: {}", e.getMessage());
            answer = buildFallbackAnswer(message, e);
        }

        if (persist) {
            AIChat chat = new AIChat();
            chat.setUserId(userId);
            chat.setQuestion(message);
            chat.setAnswer(answer);
            chatMapper.insert(chat);
        }

        return answer;
    }

    private List<Map<String, String>> buildConversationMessages(Long userId,
                                                                String message,
                                                                String overrideSystemPrompt,
                                                                List<Map<String, String>> extraContext,
                                                                boolean appendRecognitionContext,
                                                                boolean includeHistory) {
        List<Map<String, String>> messages = new ArrayList<>();
        String prompt = StringUtils.hasText(overrideSystemPrompt) ? overrideSystemPrompt.trim() : resolveSystemPrompt();
        if (StringUtils.hasText(prompt)) {
            messages.add(buildMessage("system", prompt));
        }

        for (Map<String, String> item : extraContext == null ? Collections.<Map<String, String>>emptyList() : extraContext) {
            String role = item == null ? "" : String.valueOf(item.getOrDefault("role", "system"));
            String content = item == null ? "" : item.get("content");
            if (StringUtils.hasText(content)) {
                messages.add(buildMessage(role, content));
            }
        }

        if (appendRecognitionContext && userId != null) {
            messages.addAll(buildRecognitionContextMessages(userId));
        }

        if (includeHistory && userId != null) {
            List<AIChat> history = chatMapper.findByUserId(userId, MAX_HISTORY_ROUNDS);
            Collections.reverse(history);
            for (AIChat item : history) {
                if (StringUtils.hasText(item.getQuestion())) {
                    messages.add(buildMessage("user", trimForPrompt(item.getQuestion(), 500)));
                }
                if (StringUtils.hasText(item.getAnswer())) {
                    messages.add(buildMessage("assistant", trimForPrompt(item.getAnswer(), 700)));
                }
            }
        }

        messages.add(buildMessage("user", message));
        return messages;
    }

    private List<Map<String, String>> buildRecognitionContextMessages(Long userId) {
        List<AIRecognition> recognitions = recognitionMapper.findByUserId(userId, 1);
        if (recognitions == null || recognitions.isEmpty()) {
            return Collections.emptyList();
        }

        AIRecognition latest = recognitions.get(0);
        String resultText = trimForPrompt(parseRecognitionResult(latest), 800);
        if (!StringUtils.hasText(resultText)) {
            return Collections.emptyList();
        }
        return List.of(buildMessage("system", "用户最近一次识别结果如下，可作为回答参考：\n" + resultText));
    }

    private String parseRecognitionResult(AIRecognition recognition) {
        if (recognition == null || !StringUtils.hasText(recognition.getResult())) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(recognition.getResult());
            String recognitionType = node.path("recognitionType").asText("");
            String label = node.path("riceType").asText("");
            if (!StringUtils.hasText(label)) {
                label = node.path("diseaseName").asText("");
            }
            String confidence = node.path("confidence").asText("");
            String suggestions = node.path("suggestions").asText("");
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("recognitionType", recognitionType);
            summary.put("label", label);
            summary.put("confidence", confidence);
            summary.put("suggestions", suggestions);
            summary.put("imageUrl", recognition.getImageUrl());
            summary.put("createTime", recognition.getCreateTime());
            return safeToJson(summary);
        } catch (Exception e) {
            return recognition.getResult();
        }
    }

    private String callBailian(List<Map<String, String>> messages) {
        ApiKeyResolution keyResolution = resolveApiKeyWithSource();
        String apiKey = keyResolution.key();
        if (!keyResolution.configured()) {
            throw new IllegalStateException("未配置百炼 API Key，请在 application.yml 的 bailian.api-key 或环境变量 DASHSCOPE_API_KEY/BAILIAN_API_KEY 配置");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", bailianModel);
        payload.put("messages", messages);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("构建百炼请求失败：" + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bailianBaseUrl))
                .timeout(Duration.ofSeconds(Math.max(5, bailianTimeoutSeconds)))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("调用百炼接口被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("调用百炼接口失败：" + e.getMessage(), e);
        }

        return parseBailianResponse(response);
    }

    private String parseBailianResponse(HttpResponse<byte[]> response) {
        byte[] body = response.body() == null ? new byte[0] : response.body();

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("解析百炼响应失败：" + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorMessage = extractErrorMessage(root);
            throw new RuntimeException("百炼接口异常(" + response.statusCode() + ")：" + errorMessage);
        }

        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.trim().isEmpty()) {
            content = root.path("output").path("text").asText("");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("百炼接口返回内容为空");
        }
        return content.trim();
    }

    private String extractErrorMessage(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return "未知错误";
        }
        String msg = root.path("message").asText("");
        if (msg != null && !msg.trim().isEmpty()) {
            return msg.trim();
        }
        msg = root.path("error").path("message").asText("");
        if (msg != null && !msg.trim().isEmpty()) {
            return msg.trim();
        }
        return "未知错误";
    }

    private ApiKeyResolution resolveApiKeyWithSource() {
        String dashScopeEnvKey = System.getenv("DASHSCOPE_API_KEY");
        if (StringUtils.hasText(dashScopeEnvKey)) {
            return new ApiKeyResolution(dashScopeEnvKey.trim(), "DASHSCOPE_API_KEY");
        }
        String envKey = System.getenv("BAILIAN_API_KEY");
        if (StringUtils.hasText(envKey)) {
            return new ApiKeyResolution(envKey.trim(), "BAILIAN_API_KEY");
        }
        if (StringUtils.hasText(bailianApiKey)) {
            return new ApiKeyResolution(bailianApiKey.trim(), "application.yml:bailian.api-key");
        }
        return new ApiKeyResolution("", "none");
    }

    private String resolveSystemPrompt() {
        try {
            SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, "ai")
                    .last("LIMIT 1"));
            if (config != null && StringUtils.hasText(config.getConfigValue())) {
                JsonNode node = objectMapper.readTree(config.getConfigValue());
                String dynamicPrompt = node.path("prompt").asText("");
                if (StringUtils.hasText(dynamicPrompt)) {
                    return dynamicPrompt.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return bailianSystemPrompt;
    }

    private Map<String, Object> parseAuditResponse(String raw) {
        String normalized = String.valueOf(raw == null ? "" : raw).trim();
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            normalized = normalized.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(normalized);
            boolean violation = node.path("violation").asBoolean(false);
            String reason = node.path("reason").asText(violation ? "AI判定存在违规风险" : "未发现明显违规风险");
            String confidence = node.path("confidence").asText("MEDIUM");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("violation", violation);
            result.put("reason", StringUtils.hasText(reason) ? reason.trim() : (violation ? "AI判定存在违规风险" : "未发现明显违规风险"));
            result.put("confidence", confidence);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("解析审核结果失败：" + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildLocalAuditResult(Map<String, Object> postPayload) {
        String title = trimForPrompt(String.valueOf(postPayload == null ? "" : postPayload.getOrDefault("title", "")), 200);
        String content = trimForPrompt(String.valueOf(postPayload == null ? "" : postPayload.getOrDefault("content", "")), 2000);
        String merged = (title + "\n" + content).toLowerCase();

        List<String> riskyKeywords = List.of("赌博", "博彩", "约炮", "色情", "裸聊", "毒品", "代开发票", "刷单", "兼职日结", "加微信", "vx", "贷款", "套现", "诈骗");
        for (String keyword : riskyKeywords) {
            if (merged.contains(keyword)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("violation", true);
                result.put("reason", "命中敏感词“" + keyword + "”，建议人工复核后再决定是否发布");
                result.put("confidence", "HIGH");
                return result;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("violation", false);
        result.put("reason", "未发现明显违规风险");
        result.put("confidence", "LOW");
        return result;
    }

    private Map<String, String> buildMessage(String role, String content) {
        String normalizedRole = StringUtils.hasText(role) ? role.trim().toLowerCase() : "user";
        if (!List.of("system", "user", "assistant").contains(normalizedRole)) {
            normalizedRole = "user";
        }
        return Map.of("role", normalizedRole, "content", trimForPrompt(content, 4000));
    }

    private String safeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String trimForPrompt(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLen)) + "...";
    }

    private String buildFallbackAnswer(String message, Exception cause) {
        String causeMessage = cause == null ? "" : String.valueOf(cause.getMessage());
        String causeLower = causeMessage.toLowerCase();
        if (causeLower.contains("invalid_api_key") || causeLower.contains("incorrect api key")) {
            return "百炼 API Key 无效，请在阿里云百炼控制台重新生成可用 Key 后再配置。当前已切换本地应急问答。";
        }
        if (causeLower.contains("未配置百炼 api key")) {
            return "当前未配置百炼 API Key，已切换本地应急问答。你可以继续提问，我会先给出通用农业建议；配置 Key 后可获得更完整的智能分析结果。";
        }

        String input = message == null ? "" : message.trim();
        String normalized = input.toLowerCase();

        if (normalized.contains("病害") || normalized.contains("发黄") || normalized.contains("叶斑") || normalized.contains("虫")) {
            return "当前为本地应急建议：先做田间排查并隔离病株，优先使用登记在册的对症药剂，7天内复查叶色和病斑扩散情况；若连续恶化，请尽快联系农技站现场诊断。";
        }
        if (normalized.contains("施肥") || normalized.contains("追肥") || normalized.contains("氮磷钾")) {
            return "当前为本地应急建议：施肥建议遵循“少量多次”，先看苗情再追肥；孕穗期注意控氮增钾，避免一次性过量导致倒伏和贪青晚熟。";
        }
        if (normalized.contains("销售") || normalized.contains("店铺") || normalized.contains("复购") || normalized.contains("转化")) {
            return "当前为本地应急建议：先复盘近30天销量、客单价和复购商品，优先提升高销量商品库存稳定性，再结合套餐搭配和老客回访提升转化。";
        }
        if (normalized.contains("口感") || normalized.contains("品质") || normalized.contains("产量")) {
            return "当前为本地应急建议：优先控制收获含水率并分级储存，减少高温暴晒；加工环节注意碾磨强度和抛光次数，兼顾整精米率与口感。";
        }
        return "百炼服务当前不可用，已切换本地应急问答。你可以继续提问，我会先给出通用农业建议。";
    }

    private record ApiKeyResolution(String key, String source) {
        private boolean configured() {
            return StringUtils.hasText(key);
        }
    }
}
