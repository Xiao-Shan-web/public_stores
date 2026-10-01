package com.xiaoshan.fitness.service;

import lombok.Data;

/**
 * 人脸注册结果
 */
@Data
public class FaceRegisterResult {

    /** 是否成功 */
    private boolean success;

    /** 人脸特征令牌（成功时返回；百度AI face_token 或 Mock 唯一值） */
    private String faceToken;

    /** 图片平均哈希（仅 dev MockFaceService 填写，用于刷脸核销比对；百度AI实现为 null） */
    private Long imageHash;

    /** 提示信息（失败时返回原因） */
    private String message;

    public static FaceRegisterResult ok(String faceToken) {
        FaceRegisterResult r = new FaceRegisterResult();
        r.success = true;
        r.faceToken = faceToken;
        r.message = "人脸录入成功";
        return r;
    }

    public static FaceRegisterResult ok(String faceToken, Long imageHash) {
        FaceRegisterResult r = ok(faceToken);
        r.imageHash = imageHash;
        return r;
    }

    public static FaceRegisterResult fail(String message) {
        FaceRegisterResult r = new FaceRegisterResult();
        r.success = false;
        r.message = message;
        return r;
    }

}
