package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享评论实体（对应 share_comments 表）
 */
@Data
public class ShareComment {

    /** 评论ID */
    private Long id;

    /** 分享ID */
    private Long shareId;

    /** 评论用户ID */
    private Long userId;

    /** 评论内容 */
    private String content;

    /** 评论用户手机号（JOIN users 表填充） */
    private String authorPhone;

    /** 评论用户昵称（JOIN user_profiles 表填充，可能为空） */
    private String authorNickname;

    /** 评论用户头像（JOIN user_profiles 表填充，可能为空） */
    private String authorAvatar;

    /** 评论用户头像缩略图（200x200，列表展示用） */
    private String authorAvatarThumb;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
