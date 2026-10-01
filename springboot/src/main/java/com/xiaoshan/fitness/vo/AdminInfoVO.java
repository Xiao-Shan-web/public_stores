package com.xiaoshan.fitness.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员简要信息视图对象（/user-info 返回）
 * <p>
 * 作为 admin:info:{id} 的 Redis 缓存值，只含非敏感字段，不含密码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminInfoVO {

    /** 管理员ID */
    private Long id;

    /** 管理员账号 */
    private String username;

}
