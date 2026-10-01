package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信发送记录（sms_records）
 * <p>
 * 关键通知短信统一留痕：支付成功、核销成功、激活成功。
 * 无论经 LOG 演示通道还是真实云通道发送，均落库一行，便于审计与对账。
 */
@Data
public class SmsRecord {

    private Long id;
    private Long userId;
    /** 脱敏后的手机号（前 3 后 4） */
    private String phone;
    /** 模板类型：PAY_SUCCESS / ENTRY_SUCCESS / ACTIVATE_SUCCESS */
    private String templateType;
    /** 通道：LOG / ALIYUN */
    private String channel;
    /** 渲染后内容 */
    private String content;
    /** 状态：SUCCESS / FAILED / SKIPPED */
    private String status;
    private String failReason;
    /** 关联业务 ID（订单号 / 核销记录 ID / 会员卡 ID） */
    private String bizId;
    private Integer costMs;
    private LocalDateTime createdAt;
}
