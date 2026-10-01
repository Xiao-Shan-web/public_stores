package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员分享实体（对应 shares 表）
 */
@Data
public class Share {

    /** 分享ID */
    private Long id;

    /** 发布用户ID */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文内容 */
    private String content;

    /** 视频URL */
    private String videoUrl;

    /** 封面图URL */
    private String coverUrl;

    /** 图片URL列表（JSON数组字符串，前端上传时生成；最多9张） */
    private String images;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 浏览数 */
    private Integer viewCount;

    /** 状态：NORMAL-正常，HIDDEN-隐藏 */
    private String status;

    /** 发布者手机号（JOIN users 表填充） */
    private String authorPhone;

    /** 发布者头像（JOIN user_profiles 表填充，可能为空） */
    private String authorAvatar;

    /** 发布者头像缩略图（200x200，列表展示用） */
    private String authorAvatarThumb;

    /** 当前用户是否已点赞（查询时填充） */
    private Boolean liked;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
