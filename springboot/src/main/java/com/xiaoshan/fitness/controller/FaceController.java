package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.entity.FaceFeature;
import com.xiaoshan.fitness.mapper.FaceFeatureMapper;
import com.xiaoshan.fitness.service.FaceRegisterResult;
import com.xiaoshan.fitness.service.FaceService;
import com.xiaoshan.fitness.service.FaceServiceException;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 人脸录入接口（用户端，独立模块，不再耦合会员卡激活）
 * <p>
 * 流程：上传人脸图片 → FaceService.registerFace 生成 face_token → 写入 face_features
 * 会员卡激活请走 POST /api/v1/user/membership/activate（MembershipController）
 * <p>
 * 由 face.mode 切换识别服务：mock（缺省本地哈希比对）/ baidu（百度 AI 人脸 V3）。
 */
@RestController
@RequestMapping("/api/v1/user/face")
@RequiredArgsConstructor
@Slf4j
public class FaceController {

    private final JwtUtil jwtUtil;
    private final FaceService faceService;
    private final FaceFeatureMapper faceFeatureMapper;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 人脸录入
     * POST /api/v1/user/face/register  body: { imageBase64 }
     */
    @PostMapping("/register")
    @Transactional
    public Result<Map<String, Object>> register(@RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        String imageBase64 = body.get("imageBase64") == null ? null : body.get("imageBase64").toString().trim();
        // 去除 data:image/...;base64, 前缀（前端 canvas toDataURL 会带）
        if (imageBase64 != null && imageBase64.contains(",")) {
            int idx = imageBase64.indexOf(",");
            imageBase64 = imageBase64.substring(idx + 1);
        }

        // 1. 调用人脸识别服务注册（mock：本地哈希；baidu：百度人脸 V3 user/add）
        FaceRegisterResult regResult;
        try {
            regResult = faceService.registerFace(userId, imageBase64);
        } catch (FaceServiceException e) {
            // 服务级错误（网络/鉴权/配额）：不写本地人脸行，直接返回友好原因
            log.warn("人脸录入服务异常：userId={}，reason={}", userId, e.getMessage());
            return Result.fail(e.getMessage());
        }
        if (!regResult.isSuccess()) {
            return Result.fail(regResult.getMessage());
        }
        String faceToken = regResult.getFaceToken();
        Long imageHash = regResult.getImageHash();

        // 2. 写入 face_features（已有则更新 face_token + image_hash）
        //    写库失败必须补偿删除刚写入的识别底库，否则会出现"底库已有人脸、系统却显示未录入"，
        //    用户刷脸能匹配上却被核销侧的登记校验拦住，属于典型的状态不一致（静默失败）。
        try {
            Optional<FaceFeature> existed = faceFeatureMapper.findByUserId(userId);
            if (existed.isPresent()) {
                faceFeatureMapper.updateFaceToken(userId, faceToken, imageHash);
                log.info("用户{}更新人脸特征（faceToken={}）", userId, faceToken);
            } else {
                FaceFeature ff = new FaceFeature();
                ff.setId(idGenerator.nextId());
                ff.setUserId(userId);
                ff.setFaceToken(faceToken);
                ff.setImageHash(imageHash);
                faceFeatureMapper.insert(ff);
                log.info("用户{}录入人脸特征（faceToken={}）", userId, faceToken);
            }
        } catch (Exception e) {
            log.error("人脸登记写库失败，补偿删除识别底库：userId={}", userId, e);
            compensateDeleteFace(userId);
            return Result.fail("人脸录入失败，请稍后重试");
        }

        // 3. 返回结果（不再自动激活会员卡，激活走独立接口）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("faceToken", faceToken);
        data.put("registered", true);
        return Result.ok(data, "人脸录入成功");
    }

    /**
     * 查询当前用户人脸录入状态
     * GET /api/v1/user/face/status
     * <p>
     * registered 只代表"系统有登记"，matchable 才代表"当前识别模式下底库真的能比对上"。
     * 两者不一致时下发 hint，避免用户端显示"已录入"却永远刷不上脸。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        FaceFeature feature = faceFeatureMapper.findByUserId(userId).orElse(null);
        boolean registered = feature != null;
        boolean matchable = faceService.canMatch(feature);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("registered", registered);
        data.put("matchable", matchable);
        data.put("faceToken", feature == null ? null : feature.getFaceToken());
        if (registered && !matchable) {
            data.put("hint", "当前人脸识别模式下未找到你的人脸底库，请重新录入后才能刷脸核销");
        }
        return Result.ok(data);
    }

    /**
     * 注销人脸
     * DELETE /api/v1/user/face
     * <p>
     * 删除顺序很关键：<b>先删系统登记，再删识别底库</b>。
     * 若反过来（底库先删、登记删失败），会出现"系统说已录入、底库却没有人脸"，
     * 用户以为能刷脸却永远匹配不上；而当前顺序最坏情况是"底库有残留、系统说未录入"，
     * 该残留会被核销侧的登记校验拦下（fail-closed），不会造成误放行。
     */
    @DeleteMapping
    public Result<Map<String, Object>> unregister(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        boolean registered = faceFeatureMapper.findByUserId(userId).isPresent();
        if (registered) {
            faceFeatureMapper.deleteByUserId(userId);
            log.info("用户{}已注销人脸登记", userId);
        }

        boolean galleryCleared = true;
        try {
            faceService.deleteFace(userId);
        } catch (FaceServiceException e) {
            galleryCleared = false;
            log.error("注销人脸：识别底库清理失败 userId={}，reason={}", userId, e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("registered", false);
        data.put("galleryCleared", galleryCleared);
        if (!galleryCleared) {
            String msg = "人脸已注销；识别底库清理失败，请联系管理员处理";
            data.put("message", msg);
            return Result.ok(data, msg);
        }
        return Result.ok(data, registered ? "人脸已注销" : "当前未录入人脸");
    }

    // ==================== 私有辅助 ====================

    /**
     * 补偿删除识别底库（best-effort）：登记写库失败后调用，避免底库残留无法比对的人脸。
     */
    private void compensateDeleteFace(Long userId) {
        try {
            faceService.deleteFace(userId);
        } catch (Exception ex) {
            log.error("补偿删除识别底库失败，底库可能存在残留人脸 userId={}", userId, ex);
        }
    }

    private Long currentUserId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        if (!JwtUtil.TYPE_USER.equals(jwtUtil.getTypeFromToken(token))) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

}
