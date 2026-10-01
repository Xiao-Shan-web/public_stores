package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员实体（对应 admins 表）
 * 管理员使用账号 + 密码（BCrypt 加密）登录，与普通用户分表存储
 */
@Data
public class Admin {

    /** 管理员ID（雪花算法生成） */
    private Long id;

    /** 管理员账号 */
    private String username;

    /** 密码（BCrypt 加密） */
    private String password;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
