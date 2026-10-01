package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风控事件（risk_events）：异常登录 / 异常核销自动检测产生
 */
@Data
public class RiskEvent {

    private Long id;
    /** LOGIN_FAIL_BURST / LOGIN_UNUSUAL_TIME / ENTRY_FREQ / ENTRY_NIGHT */
    private String eventType;
    /** LOW / MEDIUM / HIGH */
    private String riskLevel;
    /** USER / ADMIN */
    private String subjectType;
    private Long subjectId;
    private String subjectName;
    private String detailJson;
    /** OPEN / IGNORED / HANDLED */
    private String status;
    private Long handlerId;
    private String handleRemark;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
