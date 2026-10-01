package com.xiaoshan.fitness.service.smartjava;

import cn.smartjavaai.common.config.Config;
import cn.smartjavaai.common.entity.R;
import cn.smartjavaai.common.entity.face.FaceSearchResult;
import cn.smartjavaai.common.enums.DeviceEnum;
import cn.smartjavaai.common.enums.SimilarityType;
import cn.smartjavaai.face.config.FaceDetConfig;
import cn.smartjavaai.face.config.FaceRecConfig;
import cn.smartjavaai.face.entity.FaceRegisterInfo;
import cn.smartjavaai.face.entity.FaceSearchParams;
import cn.smartjavaai.face.enums.FaceDetModelEnum;
import cn.smartjavaai.face.enums.FaceRecModelEnum;
import cn.smartjavaai.face.factory.FaceDetModelFactory;
import cn.smartjavaai.face.factory.FaceRecModelFactory;
import cn.smartjavaai.face.model.facedect.FaceDetModel;
import cn.smartjavaai.face.model.facerec.FaceRecModel;
import cn.smartjavaai.face.vector.config.SQLiteConfig;
import com.xiaoshan.fitness.config.FaceProperties;
import com.xiaoshan.fitness.entity.FaceFeature;
import com.xiaoshan.fitness.service.FaceRegisterResult;
import com.xiaoshan.fitness.service.FaceService;
import com.xiaoshan.fitness.service.FaceServiceException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

/**
 * SmartJavaAI 离线人脸识别实现（face.mode=smartjava 时装配）。
 * <p>
 * 模型由 DJL 自动下载（首次启动约 100MB），缓存在本地，后续离线运行。
 * 人脸底库使用 SQLite（自动创建），注册时以 userId 为 face ID，搜索时直接解析回 userId。
 * <p>
 * 语义约定与 baidu 实现一致：
 * searchFace 返回 null = 正常未匹配；抛 FaceServiceException = 服务故障/无人脸等，核销必须按失败处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "face", name = "mode", havingValue = "smartjava")
public class SmartJavaFaceService implements FaceService {

    private final FaceProperties faceProperties;
    private volatile FaceRecModel faceRecModel;

    @PostConstruct
    void init() {
        FaceProperties.SmartJava cfg = faceProperties.getSmartjava();

        // 创建模型缓存目录
        try {
            Path cacheDir = Paths.get(cfg.getModelCachePath());
            Files.createDirectories(cacheDir);
            Config.setCachePath(cacheDir.toAbsolutePath().toString());
            log.info("SmartJavaAI 模型缓存目录：{}", cacheDir.toAbsolutePath());
        } catch (Exception e) {
            log.warn("创建模型缓存目录失败，使用默认路径", e);
        }

        // 1. 创建人脸检测模型（RETINA_FACE 由 DJL 自动下载，无需手动设置 modelPath）
        log.info("正在初始化 SmartJavaAI 人脸检测模型（RETINA_FACE，首次启动自动下载约 110MB）...");
        FaceDetConfig detConfig = new FaceDetConfig();
        detConfig.setModelEnum(FaceDetModelEnum.RETINA_FACE);
        detConfig.setDevice(DeviceEnum.CPU);
        FaceDetModel faceDetModel = FaceDetModelFactory.getInstance().getModel(detConfig);

        // 2. 创建人脸识别模型，注入检测模型（FACENET_MODEL 同样由 DJL 自动下载，约 104MB）
        FaceRecConfig config = new FaceRecConfig();
        config.setModelEnum(FaceRecModelEnum.FACENET_MODEL);
        config.setDevice(DeviceEnum.CPU);
        config.setCropFace(true);
        config.setAlign(true);
        config.setDetectModel(faceDetModel);

        // SQLite 人脸底库（自动创建数据库及表）
        SQLiteConfig vectorDBConfig = new SQLiteConfig();
        vectorDBConfig.setSimilarityType(SimilarityType.IP);
        config.setVectorDBConfig(vectorDBConfig);

        log.info("正在初始化 SmartJavaAI 人脸识别模型（首次启动将自动下载模型，请保持网络畅通）...");
        faceRecModel = FaceRecModelFactory.getInstance().getModel(config);
        log.info("SmartJavaAI 人脸识别模型初始化完成，相似度阈值={}", cfg.getSimilarityThreshold());
    }

    @Override
    public FaceRegisterResult registerFace(Long userId, String imageBase64) {
        if (userId == null) {
            return FaceRegisterResult.fail("用户ID不能为空");
        }
        if (imageBase64 == null || imageBase64.isBlank()) {
            return FaceRegisterResult.fail("人脸图片不能为空");
        }

        String faceId = String.valueOf(userId);
        try {
            waitForFaceDbLoad();

            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);

            // 重新录入时先删除旧人脸（不存在则忽略）
            try {
                faceRecModel.removeRegister(faceId);
                log.info("已删除用户{}的旧人脸数据", userId);
            } catch (Exception ignored) {
                // 首次录入，无旧数据
            }

            // 提取人脸特征（取分数最高的人脸）
            R<float[]> featureResult = faceRecModel.extractTopFaceFeature(imageBytes);
            if (!featureResult.isSuccess()) {
                log.warn("SmartJavaAI 人脸特征提取失败：userId={}，reason={}", userId, featureResult.getMessage());
                return FaceRegisterResult.fail("无法识别人脸，请正对镜头、保证面部完整入镜");
            }

            // 注册到 SQLite 人脸库
            FaceRegisterInfo info = new FaceRegisterInfo();
            info.setId(faceId);
            R<String> registerResult = faceRecModel.register(info, featureResult.getData());
            if (!registerResult.isSuccess()) {
                log.warn("SmartJavaAI 人脸注册失败：userId={}，reason={}", userId, registerResult.getMessage());
                return FaceRegisterResult.fail("人脸录入失败：" + registerResult.getMessage());
            }

            String faceToken = TOKEN_PREFIX_SMARTJAVA + userId;
            log.info("SmartJavaAI 人脸注册成功：userId={}，faceId={}", userId, registerResult.getData());
            return FaceRegisterResult.ok(faceToken);

        } catch (FaceServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("SmartJavaAI 人脸注册异常：userId={}", userId, e);
            return FaceRegisterResult.fail("人脸录入服务异常，请稍后重试");
        }
    }

    @Override
    public Long searchFace(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new FaceServiceException("人脸图片不能为空");
        }

        try {
            waitForFaceDbLoad();

            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);

            // 提取人脸特征
            R<float[]> featureResult = faceRecModel.extractTopFaceFeature(imageBytes);
            if (!featureResult.isSuccess()) {
                log.warn("SmartJavaAI 人脸特征提取失败：reason={}", featureResult.getMessage());
                throw new FaceServiceException("未检测到人脸，请正对镜头");
            }

            // 1:N 搜索
            FaceSearchParams params = new FaceSearchParams();
            params.setTopK(1);
            params.setThreshold((float) faceProperties.getSmartjava().getSimilarityThreshold());

            List<FaceSearchResult> results = faceRecModel.search(featureResult.getData(), params);
            if (results == null || results.isEmpty()) {
                log.info("SmartJavaAI 人脸搜索：底库无匹配用户");
                return null;
            }

            FaceSearchResult best = results.get(0);
            String matchedId = best.getId() == null ? null : String.valueOf(best.getId());
            if (matchedId == null || matchedId.isBlank() || "null".equals(matchedId)) {
                log.warn("SmartJavaAI 人脸搜索：命中结果缺少 ID");
                return null;
            }

            try {
                Long matchedUserId = Long.parseLong(matchedId);
                log.info("SmartJavaAI 人脸搜索：匹配成功 userId={}，similarity={}",
                        matchedUserId, best.getSimilarity());
                return matchedUserId;
            } catch (NumberFormatException e) {
                log.error("SmartJavaAI 人脸搜索：faceId 非本系统用户ID：{}", matchedId);
                return null;
            }

        } catch (FaceServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("SmartJavaAI 人脸搜索异常", e);
            throw new FaceServiceException("人脸服务暂不可用，请稍后重试", e);
        }
    }

    /**
     * 本实现的人脸库是 SQLite 向量库，只有由本类写入（token 带 SMARTJAVA_FACE_ 前缀）的
     * 登记才可能存在于库中；mock/baidu 时期的历史登记无法被检索到，需用户重新录入。
     */
    @Override
    public boolean canMatch(FaceFeature feature) {
        return feature != null
                && feature.getFaceToken() != null
                && feature.getFaceToken().startsWith(TOKEN_PREFIX_SMARTJAVA);
    }

    /**
     * 从 SQLite 人脸库移除该用户（注销人脸）。
     * <p>
     * SmartJavaAI 的 removeRegister 对"库中不存在"的 id 也可能报错，因此统一按幂等处理：
     * 仅记录日志，不向调用方抛错；真正的兜底由核销侧的登记校验完成（fail-closed）。
     */
    @Override
    public void deleteFace(Long userId) {
        if (faceRecModel == null) {
            throw new FaceServiceException("人脸识别模型尚未初始化，暂时无法注销人脸");
        }
        String faceId = String.valueOf(userId);
        try {
            faceRecModel.removeRegister(faceId);
            log.info("SmartJavaAI 已移除人脸底库记录：userId={}", userId);
        } catch (Exception e) {
            log.warn("SmartJavaAI 移除人脸底库记录失败（按不存在处理）：userId={}，reason={}",
                    userId, e.getMessage());
        }
    }

    /**
     * 等待 SQLite 人脸库异步加载完成（最多等 10 秒）。
     */
    private void waitForFaceDbLoad() {
        if (faceRecModel == null) {
            throw new FaceServiceException("人脸识别模型尚未初始化");
        }
        int maxWait = 100;
        int waited = 0;
        while (!faceRecModel.isLoadFaceCompleted() && waited < maxWait) {
            try {
                Thread.sleep(100);
                waited++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FaceServiceException("等待人脸库加载被中断");
            }
        }
        if (!faceRecModel.isLoadFaceCompleted()) {
            log.warn("SmartJavaAI 人脸库加载超时（等待 {} 次），继续执行可能影响查询结果", waited);
        }
    }
}
