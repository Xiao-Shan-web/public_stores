package com.xiaoshan.fitness.service;

/**
 * 人脸识别服务异常：网络故障、鉴权失败、配额超限、图片无人脸/质量不合格等。
 * <p>
 * 与 FaceService.searchFace 返回 null（正常"未匹配"）严格区分：
 * 抛异常表示本次识别未能得出有效结论，核销必须按失败处理并记录原因，绝不放行。
 */
public class FaceServiceException extends RuntimeException {

    public FaceServiceException(String message) {
        super(message);
    }

    public FaceServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
