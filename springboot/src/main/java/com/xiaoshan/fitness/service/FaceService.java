package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.FaceFeature;

/**
 * 人脸识别适配器（由 face.mode 选择实现）
 * <p>
 * mock（缺省）：MockFaceService 基于本地图片平均哈希比对，dev 兜底，无需密钥；
 * baidu：BaiduFaceService 调百度 AI 人脸 V3（在线，需密钥）；
 * smartjava：SmartJavaFaceService 基于 SmartJavaAI 离线识别（DJL 自动下载模型，SQLite 人脸库）。
 * <p>
 * 语义约定：searchFace 返回 null 表示"正常未匹配"；服务故障/无人脸等不可判定场景
 * 抛 {@link FaceServiceException}，调用方必须按识别失败处理，不得放行。
 * <p>
 * <b>底库与登记分离：</b>face_features 表只记录「系统登记」（face_token 便于排查），
 * 真正参与比对的是各实现自己的底库（图片哈希 / 百度人脸组 / SQLite 向量库）。
 * 因此两者必须成对维护：录入时先写底库再写登记，注销时先删登记再删底库，
 * 并通过 {@link #canMatch} 把"登记了但底库没有"的不一致暴露给用户端。
 */
public interface FaceService {

    /**
     * mock 实现生成的 face_token 前缀。
     * <p>
     * face_token 会随登记一起落库，因此前缀可用于反查"这条登记是哪个底库写入的"，
     * 各实现据此判断切换 face.mode 后是否需要用户重新录入。
     */
    String TOKEN_PREFIX_MOCK = "MOCK_FACE_";

    /** smartjava 实现生成的 face_token 前缀（用途同 {@link #TOKEN_PREFIX_MOCK}） */
    String TOKEN_PREFIX_SMARTJAVA = "SMARTJAVA_FACE_";

    /**
     * 判断 token 是否由 mock/smartjava 这类"带前缀的离线实现"写入。
     * 百度返回的 face_token 为不透明串，不带任何前缀，可据此与离线实现的登记区分开。
     */
    static boolean hasKnownOfflinePrefix(String faceToken) {
        return faceToken != null
                && (faceToken.startsWith(TOKEN_PREFIX_MOCK) || faceToken.startsWith(TOKEN_PREFIX_SMARTJAVA));
    }

    /**
     * 注册人脸（录入）
     *
     * @param userId     用户ID
     * @param imageBase64 人脸图片（Base64，去 data:image 前缀）
     * @return 注册结果（含 face_token）
     */
    FaceRegisterResult registerFace(Long userId, String imageBase64);

    /**
     * 人脸搜索（刷脸核销时匹配 user_id）
     *
     * @param imageBase64 人脸图片（Base64）
     * @return 匹配到的用户ID；未匹配返回 null
     */
    Long searchFace(String imageBase64);

    /**
     * 从识别底库移除该用户的人脸（注销人脸时调用）。
     * <p>
     * 实现必须保证幂等：底库中本就没有该用户时不得抛错。
     *
     * @param userId 用户ID
     * @throws FaceServiceException 底库清理失败（服务故障）时抛出，调用方需记录并提示
     */
    void deleteFace(Long userId);

    /**
     * 当前识别模式下，这条登记记录能否真正参与刷脸比对。
     * <p>
     * 解决「用户端显示已录入，但刷脸核销永远匹配不上」的静默不一致：
     * 切换 face.mode 后，旧模式写入的 face_token 不在新模式的底库里
     * （例如 mock 用图片哈希、baidu 用人脸组、smartjava 用 SQLite），
     * 此时登记仍存在但底库没有对应人脸，必须提示用户重新录入。
     *
     * @param feature 该用户的登记记录，null 表示未登记
     * @return true 表示该用户能参与比对；false 表示登记与当前底库不匹配
     */
    boolean canMatch(FaceFeature feature);

}
