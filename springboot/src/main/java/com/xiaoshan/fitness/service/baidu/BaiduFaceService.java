package com.xiaoshan.fitness.service.baidu;

import com.xiaoshan.fitness.config.FaceProperties;
import com.xiaoshan.fitness.entity.FaceFeature;
import com.xiaoshan.fitness.service.FaceRegisterResult;
import com.xiaoshan.fitness.service.FaceService;
import com.xiaoshan.fitness.service.FaceServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百度智能云人脸识别 V3 实现（face.mode=baidu 时装配）。
 * <p>
 * 注册：POST /rest/2.0/face/v3/faceset/user/add（action_type=REPLACE，重新录入即覆盖）；
 * 搜索：POST /rest/2.0/face/v3/search（取 user_list 第一名，score ≥ 配置阈值才映射回本地 userId）。
 * <p>
 * 错误语义严格分离：
 * 人脸内容类问题（无人脸/遮挡/图片损坏）→ 注册返回 fail、核销抛 FaceServiceException 给出可操作提示；
 * 服务类问题（网络/鉴权/配额）→ 抛 FaceServiceException，调用方一律按失败处理，绝不放行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "face", name = "mode", havingValue = "baidu")
public class BaiduFaceService implements FaceService {

    private static final String PATH_FACE_ADD = "/rest/2.0/face/v3/faceset/user/add";
    private static final String PATH_FACE_DELETE = "/rest/2.0/face/v3/faceset/user/delete";
    private static final String PATH_FACE_SEARCH = "/rest/2.0/face/v3/search";

    /** 用户不在人脸组内（注销时按幂等成功处理） */
    private static final int EC_USER_NOT_EXIST_222207 = 222207;

    /** 百度要求 Base64 编码后图片不超过 2MB */
    private static final int MAX_BASE64_LENGTH = 2 * 1024 * 1024;

    /** access_token 失效类错误码：刷新一次后重试 */
    private static final int EC_TOKEN_INVALID_110 = 110;
    private static final int EC_TOKEN_EXPIRED_111 = 111;

    private final RestClient baiduFaceRestClient;
    private final FaceProperties faceProperties;
    private final BaiduTokenManager tokenManager;

    @Override
    public FaceRegisterResult registerFace(Long userId, String imageBase64) {
        if (userId == null) {
            return FaceRegisterResult.fail("用户ID不能为空");
        }
        String image = normalizeImage(imageBase64);
        if (image == null || image.isBlank()) {
            return FaceRegisterResult.fail("人脸图片不能为空");
        }
        if (image.length() > MAX_BASE64_LENGTH) {
            return FaceRegisterResult.fail("人脸图片过大（上限 2MB），请重新拍摄");
        }

        FaceProperties.Baidu cfg = faceProperties.getBaidu();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image", image);
        body.put("image_type", "BASE64");
        body.put("group_id", cfg.getGroupId());
        body.put("user_id", String.valueOf(userId));
        // 重新录入时直接覆盖该用户在组内的人脸
        body.put("action_type", "REPLACE");
        body.put("quality_control", cfg.getQualityControl());
        body.put("liveness_control", cfg.getLivenessControl());

        Map<?, ?> resp = invokeFaceApi(PATH_FACE_ADD, body);
        Integer errorCode = errorCodeOf(resp);
        if (errorCode != null && errorCode == 0) {
            String faceToken = stringAt(resp, "result", "face_token");
            if (faceToken == null || faceToken.isBlank()) {
                log.error("百度人脸注册成功但缺少 face_token：{}", resp);
                return FaceRegisterResult.fail("人脸录入失败：服务商返回异常，请重试");
            }
            log.info("百度人脸注册成功：userId={}，faceToken={}", userId, faceToken);
            return FaceRegisterResult.ok(faceToken);
        }
        log.warn("百度人脸注册被拒：userId={}，errorCode={}，errorMsg={}",
                userId, errorCode, resp == null ? null : resp.get("error_msg"));
        return FaceRegisterResult.fail(faceErrorMessage(errorCode, resp, "register"));
    }

    @Override
    public Long searchFace(String imageBase64) {
        String image = normalizeImage(imageBase64);
        if (image == null || image.isBlank()) {
            throw new FaceServiceException("人脸图片不能为空");
        }
        if (image.length() > MAX_BASE64_LENGTH) {
            throw new FaceServiceException("人脸图片过大（上限 2MB），请重新拍摄");
        }

        FaceProperties.Baidu cfg = faceProperties.getBaidu();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image", image);
        body.put("image_type", "BASE64");
        body.put("group_id_list", cfg.getGroupId());
        body.put("max_user_num", 1);
        body.put("match_threshold", cfg.getScoreThreshold());
        body.put("quality_control", cfg.getQualityControl());
        body.put("liveness_control", cfg.getLivenessControl());

        Map<?, ?> resp = invokeFaceApi(PATH_FACE_SEARCH, body);
        Integer errorCode = errorCodeOf(resp);

        // 222207：底库中无匹配用户，属于正常"未匹配"
        if (errorCode != null && errorCode == 222207) {
            log.info("百度人脸搜索：底库无匹配用户");
            return null;
        }
        if (errorCode != null && errorCode != 0) {
            // 无人脸/遮挡/图片损坏等内容类问题给出可操作提示（核销失败）
            throw new FaceServiceException(faceErrorMessage(errorCode, resp, "search"));
        }

        Object userListObj = resp == null ? null : nested(resp.get("result"), "user_list");
        if (!(userListObj instanceof List<?> userList) || userList.isEmpty()
                || !(userList.get(0) instanceof Map<?, ?> best)) {
            log.info("百度人脸搜索：返回成功但 user_list 为空，按未匹配处理");
            return null;
        }

        double score = best.get("score") instanceof Number n ? n.doubleValue() : 0d;
        String baiduUserId = best.get("user_id") == null ? null : best.get("user_id").toString();
        if (baiduUserId == null || baiduUserId.isBlank()) {
            log.warn("百度人脸搜索：命中结果缺少 user_id，按未匹配处理，score={}", score);
            return null;
        }
        Long userId;
        try {
            userId = Long.parseLong(baiduUserId);
        } catch (NumberFormatException e) {
            log.error("百度人脸搜索：user_id 非本系统用户ID：{}", baiduUserId);
            return null;
        }
        if (score < cfg.getScoreThreshold()) {
            log.info("百度人脸搜索：命中 userId={} 但 score={} 低于阈值 {}，按未匹配处理",
                    userId, score, cfg.getScoreThreshold());
            return null;
        }
        log.info("百度人脸搜索：匹配成功 userId={}，score={}", userId, score);
        return userId;
    }

    /**
     * 百度人脸 token 为不透明串，不带 MOCK_FACE_ / SMARTJAVA_FACE_ 前缀。
     * 带离线前缀的登记说明是切换 face.mode 之前写入的，不在百度人脸组内，
     * 必须提示用户重新录入，否则会"显示已录入却始终匹配不上"。
     */
    @Override
    public boolean canMatch(FaceFeature feature) {
        return feature != null
                && feature.getFaceToken() != null
                && !feature.getFaceToken().isBlank()
                && !FaceService.hasKnownOfflinePrefix(feature.getFaceToken());
    }

    /**
     * 从百度人脸组删除该用户（注销人脸）。
     * <p>
     * 幂等：用户本就不在组内（222207）视为成功，不向调用方抛错。
     */
    @Override
    public void deleteFace(Long userId) {
        FaceProperties.Baidu cfg = faceProperties.getBaidu();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("group_id", cfg.getGroupId());
        body.put("user_id", String.valueOf(userId));

        Map<?, ?> resp = invokeFaceApi(PATH_FACE_DELETE, body);
        Integer errorCode = errorCodeOf(resp);
        if (errorCode != null && errorCode == 0) {
            log.info("百度人脸底库已删除：userId={}", userId);
            return;
        }
        if (errorCode != null && errorCode == EC_USER_NOT_EXIST_222207) {
            log.info("百度人脸底库中无该用户，按已注销处理：userId={}", userId);
            return;
        }
        String baiduMsg = resp == null ? null : String.valueOf(resp.get("error_msg"));
        if (baiduMsg != null && baiduMsg.toLowerCase().contains("not exist")) {
            log.info("百度人脸底库中无该用户（{}），按已注销处理：userId={}", baiduMsg, userId);
            return;
        }
        log.error("百度人脸底库删除失败：userId={}，errorCode={}，errorMsg={}", userId, errorCode, baiduMsg);
        throw new FaceServiceException("人脸底库清理失败，请联系管理员重新录入人脸");
    }

    // ==================== 百度接口调用 ====================

    /**
     * 调用人脸 V3 接口（JSON），自动拼接 access_token；token 失效时强制刷新并重试一次。
     */
    @SuppressWarnings("rawtypes")
    private Map<?, ?> invokeFaceApi(String path, Map<String, Object> body) {
        try {
            Map<?, ?> resp = postOnce(path, body, tokenManager.getAccessToken());
            Integer code = errorCodeOf(resp);
            if (code != null && (code == EC_TOKEN_INVALID_110 || code == EC_TOKEN_EXPIRED_111)) {
                log.warn("百度人脸接口提示 token 失效（{}），强制刷新后重试一次", code);
                resp = postOnce(path, body, tokenManager.forceRefresh());
            }
            return resp;
        } catch (FaceServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用百度人脸接口失败：path={}", path, e);
            throw new FaceServiceException("人脸服务暂不可用，请稍后重试", e);
        }
    }

    @SuppressWarnings("rawtypes")
    private Map<?, ?> postOnce(String path, Map<String, Object> body, String accessToken) {
        return baiduFaceRestClient.post()
                .uri(uriBuilder -> uriBuilder.path(path)
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    // ==================== 响应解析 / 错误码映射 ====================

    private Integer errorCodeOf(Map<?, ?> resp) {
        if (resp == null) {
            return null;
        }
        Object code = resp.get("error_code");
        if (code instanceof Number n) {
            return n.intValue();
        }
        if (code instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 把百度错误码转成给用户/操作员看的中文原因。
     */
    private String faceErrorMessage(Integer errorCode, Map<?, ?> resp, String scene) {
        String baiduMsg = resp == null ? null : String.valueOf(resp.get("error_msg"));
        String suffix = (baiduMsg == null || "null".equals(baiduMsg)) ? "" : "（" + baiduMsg + "）";
        if (errorCode == null) {
            log.error("百度人脸接口返回缺少 error_code：{}", resp);
            return "人脸服务返回异常，请稍后重试";
        }
        return switch (errorCode) {
            // 图片/人脸内容类
            case 222202, 222205 -> "人脸图片无法识别，请重新拍摄清晰照片";
            case 222203 -> "未检测到人脸，请正对镜头、保证面部完整入镜";
            case 222206 -> "人脸被遮挡，请摘除口罩、墨镜或移开遮挡物后重试";
            case 222207 -> "未匹配到已录入的会员人脸";
            // 配额/权限类（注册场景给管理员级提示）
            case 18, 19, 4 -> "人脸服务繁忙（触发限流/配额），请稍后重试";
            default -> "search".equals(scene)
                    ? "人脸审核失败，请重新拍摄" + suffix
                    : "人脸录入失败" + suffix;
        };
    }

    /** 去除 data:image/...;base64, 前缀与空白 */
    private String normalizeImage(String imageBase64) {
        if (imageBase64 == null) {
            return null;
        }
        String s = imageBase64.trim();
        int comma = s.indexOf(",");
        if (comma >= 0 && s.substring(0, comma).contains("base64")) {
            s = s.substring(comma + 1);
        }
        return s.replaceAll("\\s+", "");
    }

    private Object nested(Object obj, String key) {
        return obj instanceof Map<?, ?> m ? m.get(key) : null;
    }

    private String stringAt(Map<?, ?> resp, String parentKey, String childKey) {
        Object parent = resp.get(parentKey);
        Object child = nested(parent, childKey);
        return child == null ? null : child.toString();
    }
}
