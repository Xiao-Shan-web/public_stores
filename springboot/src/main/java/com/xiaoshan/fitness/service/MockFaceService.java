package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.FaceFeature;
import com.xiaoshan.fitness.mapper.FaceFeatureMapper;
import com.xiaoshan.fitness.util.FaceImageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 开发环境模拟人脸识别（基于图像平均哈希 aHash 的真实内容比对）。
 * <p>
 * registerFace：计算图片平均哈希并连同 face_token 存入 face_features。
 * searchFace：计算当前人脸图片的平均哈希，遍历已录入人脸底库逐一比对 Hamming 距离，
 *             距离 <= 阈值视为同人，返回该 userId；全部不匹配返回 null。
 * <p>
 * 同一人不同帧 Hamming 距离通常较小（< 12）能匹配；不同人 / 随便点击 / 无底库都会失败。
 * <p>
 * 由 face.mode 切换：mock（缺省，本类装配）/ baidu（BaiduFaceService 装配）。
 * 切换到 baidu 后，Mock 时期录入的底库不在百度人脸组，需用户重新录入一次。
 * <p>
 * 本实现的底库就是 face_features.image_hash 本身，因此删除登记（MySQL 行）即等于删除底库，
 * {@link #deleteFace} 无需额外动作；但只要 image_hash 缺失（baidu/smartjava 时期录入的
 * 历史数据），该用户就无法参与比对，由 {@link #canMatch} 暴露给用户端提示重新录入。
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "face", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockFaceService implements FaceService {

    private final FaceFeatureMapper faceFeatureMapper;

    @Override
    public FaceRegisterResult registerFace(Long userId, String imageBase64) {
        if (userId == null) {
            return FaceRegisterResult.fail("用户ID不能为空");
        }
        if (imageBase64 == null || imageBase64.isBlank()) {
            return FaceRegisterResult.fail("人脸图片不能为空");
        }
        // 计算图片平均哈希；无法解码/非图片格式 → 录入失败（防止存入无法比对的底库）
        Long imageHash = FaceImageUtil.averageHash(imageBase64);
        if (imageHash == null) {
            return FaceRegisterResult.fail("无法识别人脸图片，请重新拍摄清晰照片");
        }
        String faceToken = TOKEN_PREFIX_MOCK + userId + "_" + System.currentTimeMillis();
        log.info("Mock 人脸注册成功：userId={}，faceToken={}，imageHash={}", userId, faceToken, imageHash);
        return FaceRegisterResult.ok(faceToken, imageHash);
    }

    @Override
    public Long searchFace(String imageBase64) {
        // 1. 当前人脸图片不能为空，且必须可解码为有效图片
        Long capturedHash = FaceImageUtil.averageHash(imageBase64);
        if (capturedHash == null) {
            log.warn("Mock 人脸搜索：当前图片无效或为空，比对失败");
            return null;
        }

        // 2. 读取已录入人脸底库（仅有 image_hash 的记录可参与比对）
        List<FaceFeature> gallery = faceFeatureMapper.findAll();
        if (gallery.isEmpty()) {
            log.warn("Mock 人脸搜索：底库为空（无任何已录入人脸），比对失败");
            return null;
        }

        // 3. 逐一比对 Hamming 距离，取最小者
        Long bestUserId = null;
        int bestDistance = Integer.MAX_VALUE;
        for (FaceFeature ff : gallery) {
            if (ff.getImageHash() == null) {
                continue;
            }
            int dist = FaceImageUtil.hammingDistance(capturedHash, ff.getImageHash());
            if (dist < bestDistance) {
                bestDistance = dist;
                bestUserId = ff.getUserId();
            }
        }

        // 4. 最小距离 <= 阈值 → 视为同人，返回 userId；否则未匹配
        if (bestUserId != null && bestDistance <= FaceImageUtil.MATCH_THRESHOLD) {
            log.info("Mock 人脸搜索：匹配成功 userId={}，Hamming 距离={}（阈值{}）",
                    bestUserId, bestDistance, FaceImageUtil.MATCH_THRESHOLD);
            return bestUserId;
        }
        log.info("Mock 人脸搜索：未匹配（最小距离={}，阈值={}）", bestDistance, FaceImageUtil.MATCH_THRESHOLD);
        return null;
    }

    /**
     * mock 底库就是 face_features 里的 image_hash：没有哈希就无法参与比对，
     * 必须让用户端提示重新录入，而不是显示"已录入"却永远刷不上脸。
     */
    @Override
    public boolean canMatch(FaceFeature feature) {
        return feature != null && feature.getImageHash() != null;
    }

    /**
     * mock 底库与登记同源（同一行 face_features），删除登记即已删除底库，无需额外动作。
     * 保留非空实现是为了让"注销人脸"的语义在三种模式下一致。
     */
    @Override
    public void deleteFace(Long userId) {
        log.info("Mock 人脸底库与登记同源，无需额外删除：userId={}", userId);
    }

}
