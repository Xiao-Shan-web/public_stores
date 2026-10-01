package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户投诉（complaints）
 */
@Data
public class Complaint {

    private Long id;
    private Long userId;
    /** SERVICE/ORDER/ENTRY/CARD/OTHER */
    private String type;
    private String title;
    private String content;
    /** 凭证图片 URL JSON 数组 */
    private String imagesJson;
    private String bizType;
    private String bizId;
    private Long storeId;
    private String contactPhone;
    /** PENDING/PROCESSING/ARBITRATING/RESOLVED/CLOSED */
    private String status;
    private String adminReply;
    private Long handlerAdminId;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 投诉人头像（JOIN user_profiles 表填充，可能为空） */
    private String avatar;

    /** 投诉人头像缩略图（200x200，列表展示用） */
    private String avatarThumb;
}
