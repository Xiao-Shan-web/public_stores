package com.xiaoshan.fitness.service.baidu;

import com.xiaoshan.fitness.config.FaceProperties;
import com.xiaoshan.fitness.service.FaceServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 百度 AI access_token 获取与缓存。
 * <p>
 * OAuth 2.0 client_credentials：token 有效期通常 30 天，提前 1 天视为过期；
 * 并发刷新加锁；人脸接口遇到 110/111（token 失效）时由 {@link #forceRefresh()} 强制刷新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "face", name = "mode", havingValue = "baidu")
public class BaiduTokenManager {

    /** token 提前过期余量：1 天 */
    private static final long EXPIRE_MARGIN_MS = 24L * 60 * 60 * 1000;

    private final RestClient baiduFaceRestClient;
    private final FaceProperties faceProperties;

    private volatile String cachedToken;
    private volatile long expireAtMs;

    /**
     * 获取有效 access_token（必要时刷新）。
     */
    public String getAccessToken() {
        String token = cachedToken;
        if (token != null && System.currentTimeMillis() < expireAtMs) {
            return token;
        }
        synchronized (this) {
            if (cachedToken == null || System.currentTimeMillis() >= expireAtMs) {
                refresh();
            }
            return cachedToken;
        }
    }

    /**
     * 强制刷新（人脸接口返回 token 失效错误码后调用），返回新 token。
     */
    public synchronized String forceRefresh() {
        refresh();
        return cachedToken;
    }

    private void refresh() {
        FaceProperties.Baidu cfg = faceProperties.getBaidu();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", cfg.getApiKey());
        form.add("client_secret", cfg.getSecretKey());

        Map<?, ?> resp;
        try {
            resp = baiduFaceRestClient.post()
                    .uri("/oauth/2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.error("获取百度 AI access_token 被拒：HTTP {}，body={}", status, e.getResponseBodyAsString());
            if (status == 401 || status == 403) {
                throw new FaceServiceException(
                        "人脸服务鉴权失败：API Key / Secret Key 无效或未授权，请检查配置", e);
            }
            throw new FaceServiceException("人脸服务鉴权失败（HTTP " + status + "），请稍后重试", e);
        } catch (Exception e) {
            log.error("获取百度 AI access_token 失败（网络错误）", e);
            throw new FaceServiceException("人脸服务暂不可用：无法连接鉴权服务，请检查网络后重试", e);
        }

        if (!(resp != null && resp.get("access_token") instanceof String token && !token.isBlank())) {
            String error = resp == null ? "空响应"
                    : (resp.get("error") == null ? "未知错误" : String.valueOf(resp.get("error")));
            String desc = resp == null || resp.get("error_description") == null
                    ? "" : String.valueOf(resp.get("error_description"));
            log.error("获取百度 AI access_token 被拒：error={}, description={}", error, desc);
            throw new FaceServiceException("人脸服务鉴权失败：" + error
                    + (desc.isBlank() || "null".equals(desc) ? "" : "（" + desc + "）")
                    + "，请检查 API Key / Secret Key");
        }

        long expiresInMs = 30L * 24 * 60 * 60 * 1000;
        Object expiresIn = resp.get("expires_in");
        if (expiresIn instanceof Number n) {
            expiresInMs = n.longValue() * 1000;
        }
        this.cachedToken = token;
        this.expireAtMs = System.currentTimeMillis() + Math.max(expiresInMs - EXPIRE_MARGIN_MS, 0);
        log.info("百度 AI access_token 刷新成功，有效期约 {} 秒", expiresInMs / 1000);
    }
}
