package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人脸特征实体（对应 face_features 表）
 * 用于刷脸核销时的人脸匹配，每用户最多一条
 */
@Data
public class FaceFeature {

    /** ID（雪花算法生成） */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 人脸特征令牌（百度AI face_token 或 Mock 唯一值） */
    private String faceToken;

    /** 图片平均哈希（Mock 比对用；百度AI实现为空） */
    private Long imageHash;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
