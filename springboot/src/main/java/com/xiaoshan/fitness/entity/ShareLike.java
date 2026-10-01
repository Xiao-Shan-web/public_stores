package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享点赞实体（对应 share_likes 表）
 */
@Data
public class ShareLike {

    /** 点赞ID */
    private Long id;

    /** 分享ID */
    private Long shareId;

    /** 点赞用户ID */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
