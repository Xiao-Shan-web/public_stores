package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（对应 users 表）
 * 用户无需密码，仅靠手机号一键登录；角色由分表区分（admins 为管理员）
 */
@Data
public class User {

    /** 用户ID（雪花算法生成） */
    private Long id;

    /** 手机号（登录凭证） */
    private String phone;

    /** 第三方应用ID（微信小程序openid等） */
    private String appId;

    /** 账号状态：1-正常，0-禁用（TINYINT(1)，统一用 Integer 避免 JDBC 映射为 Boolean） */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
