package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服消息实体（对应 customer_service_messages 表）
 * is_read 语义：接收方是否已读
 * - sender_type=USER 的记录：is_read 表示客服是否已读
 * - sender_type=ADMIN 的记录：is_read 表示用户是否已读
 */
@Data
public class CustomerServiceMessage {

    /** 发送方类型：用户 */
    public static final String SENDER_USER = "USER";
    /** 发送方类型：客服 */
    public static final String SENDER_ADMIN = "ADMIN";

    /** ID（雪花算法生成） */
    private Long id;

    /** 用户ID（会话归属） */
    private Long userId;

    /** 发送方类型：USER-用户，ADMIN-客服 */
    private String senderType;

    /** 消息内容（纯文本，最长500字） */
    private String content;

    /** 接收方是否已读：0-未读，1-已读 */
    private Integer isRead;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
