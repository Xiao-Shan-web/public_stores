package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员操作审计日志（admin_audit_logs），由拦截器自动记录写操作
 */
@Data
public class AdminAuditLog {

    private Long id;
    private Long adminId;
    private String username;
    private String module;
    /** HTTP 动作：POST/PUT/DELETE/PATCH */
    private String action;
    private String method;
    private String uri;
    /** 已脱敏的参数摘要 */
    private String paramSummary;
    private String ip;
    /** SUCCESS / FAIL */
    private String result;
    private Integer costMs;
    private LocalDateTime createdAt;
}
