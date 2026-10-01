package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户资料实体（对应 user_profiles 表，与 users 一对一）
 * 存放头像、昵称、真实姓名、性别、生日、个人简介
 */
@Data
public class UserProfile {

    /** 用户ID（关联 users.id，主键） */
    private Long userId;

    /** 头像访问地址（如 /uploads/avatar/xxx.jpg，最大边长 512） */
    private String avatar;

    /** 头像缩略图地址（200x200，列表/卡片展示用，降低页面加载体积） */
    private String avatarThumb;

    /** 昵称 */
    private String nickname;

    /** 真实姓名 */
    private String realName;

    /** 性别：MALE-男，FEMALE-女，UNKNOWN-保密 */
    private String gender;

    /** 生日 */
    private LocalDate birthday;

    /** 个人简介 */
    private String bio;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
