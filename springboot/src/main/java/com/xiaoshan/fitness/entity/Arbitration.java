package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 平台仲裁（arbitrations），由投诉升级产生
 */
@Data
public class Arbitration {

    private Long id;
    private Long complaintId;
    private Long userId;
    private String reason;
    /** INVESTIGATING/RULING/DONE */
    private String status;
    /** SUPPORT_USER/SUPPORT_PLATFORM/PARTIAL */
    private String result;
    private String decision;
    private BigDecimal compensationAmount;
    private Long arbitratorAdminId;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
