package com.xiaoshan.fitness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoshan.fitness.util.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百度千帆（ModelBuilder v2，OpenAI 兼容）大模型客户端。
 * <p>
 * 零新增依赖：使用 JDK 内置 {@link HttpClient} + Jackson 完成调用。
 * <ul>
 *   <li>鉴权：AK/SK 经 OAuth 2.0 换 access_token（有效期约 30 天），本地缓存并提前 1 小时刷新；</li>
 *   <li>对话：POST {base}/v2/chat/completions，Bearer Token，OpenAI 兼容 messages 结构；</li>
 *   <li>容错：连接/读超时、非 2xx、业务错误码统一抛 {@link BusinessException}(503)，
 *       由上层 Service 决定降级（回退本地规则/友好提示），不把堆栈抛给用户。</li>
 * </ul>
 * 未配置 AK/SK 时 {@link #isConfigured()} 返回 false，调用方应走本地规则，不发起真实请求。
 */
@Slf4j
@Component
public class QianfanLlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${ai.qianfan.enabled:false}")
    private boolean enabled;
    @Value("${ai.qianfan.access-key-id:}")
    private String accessKeyId;
    @Value("${ai.qianfan.secret-access-key:}")
    private String secretAccessKey;
    /** 默认使用免费/低速模型，降低开通门槛；可在配置切换为 ernie-4.5-turbo-128k 等 */
    @Value("${ai.qianfan.model:ernie-speed-128k}")
    private String model;
    @Value("${ai.qianfan.base-url:https://qianfan.baidubce.com}")
    private String baseUrl;
    @Value("${ai.qianfan.token-url:https://aip.baidubce.com/oauth/2.0/token}")
    private String tokenUrl;
    @Value("${ai.qianfan.connect-timeout-ms:3000}")
    private int connectTimeoutMs;
    @Value("${ai.qianfan.read-timeout-ms:20000}")
    private int readTimeoutMs;
    @Value("${ai.qianfan.temperature:0.7}")
    private double temperature;

    private HttpClient httpClient;

    /** token 缓存 */
    private volatile String cachedToken;
    private volatile long tokenExpireAtMs;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        log.info("千帆 LLM 客户端初始化：enabled={}, model={}, configured={}",
                enabled, model, isConfigured());
    }

    /** 是否已启用且配置完整（AK/SK 非空） */
    public boolean isConfigured() {
        return enabled
                && accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
    }

    public String getModel() {
        return model;
    }

    /**
     * 单轮对话（无历史）。
     *
     * @param systemPrompt 系统提示（人设/约束/输出格式）
     * @param userPrompt   用户输入
     * @return 模型文本（要求 JSON 时由调用方解析）
     */
    public String chat(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = List.of(
                msg("system", systemPrompt),
                msg("user", userPrompt));
        return chatMessages(messages, false);
    }

    /**
     * 多轮对话。
     *
     * @param messages OpenAI 风格消息列表（role/system|user|assistant + content）
     * @param jsonMode 是否要求模型输出严格 JSON（通过 response_type=json_object，模型不支持时由 prompt 兜底）
     */
    public String chatMessages(List<Map<String, String>> messages, boolean jsonMode) {
        if (!isConfigured()) {
            throw new BusinessException(503, "AI 服务未配置，请在后台填写千帆 API 密钥");
        }
        try {
            String token = obtainToken();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", temperature);
            if (jsonMode) {
                // 千帆 v2 支持 JSON 模式；个别模型不识别该字段会忽略，prompt 中也强约束 JSON
                body.put("response_type", "json_object");
            }
            String reqJson = MAPPER.writeValueAsString(body);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v2/chat/completions"))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("千帆对话非 2xx：status={}, body={}", resp.statusCode(), abbreviate(resp.body()));
                throw new BusinessException(503, "AI 服务暂不可用，请稍后重试");
            }
            JsonNode root = MAPPER.readTree(resp.body());
            // OpenAI 兼容：choices[0].message.content
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                // 千帆业务错误（如额度不足/模型未开通）通常返回 error.code / error.message
                JsonNode err = root.path("error");
                if (!err.isMissingNode()) {
                    log.warn("千帆业务错误：code={}, message={}", err.path("error_code").asText(),
                            err.path("error_msg").asText(err.path("message").asText()));
                }
                throw new BusinessException(503, "AI 服务返回异常，请稍后重试");
            }
            return content.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("千帆对话调用异常", e);
            throw new BusinessException(503, "AI 服务连接失败，请稍后重试");
        }
    }

    /**
     * 获取（必要时刷新）access_token。双检锁 + 提前 1 小时过期，避免每次请求换 token。
     */
    private String obtainToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAtMs) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && System.currentTimeMillis() < tokenExpireAtMs) {
                return cachedToken;
            }
            String url = tokenUrl
                    + "?grant_type=client_credentials"
                    + "&client_id=" + enc(accessKeyId)
                    + "&client_secret=" + enc(secretAccessKey);
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(readTimeoutMs))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonNode root = MAPPER.readTree(resp.body());
                String token = root.path("access_token").asText(null);
                long expiresIn = root.path("expires_in").asLong(2592000L);
                if (token == null || token.isBlank()) {
                    log.warn("千帆获取 token 失败：status={}, body={}", resp.statusCode(), abbreviate(resp.body()));
                    throw new BusinessException(503, "AI 鉴权失败，请检查 API 密钥配置");
                }
                this.cachedToken = token;
                // 提前 1 小时视为过期，避免临界失效
                this.tokenExpireAtMs = System.currentTimeMillis() + (expiresIn - 3600) * 1000L;
                return token;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("千帆获取 token 异常", e);
                throw new BusinessException(503, "AI 鉴权服务连接失败");
            }
        }
    }

    private Map<String, String> msg(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 300 ? s : s.substring(0, 300);
    }
}
