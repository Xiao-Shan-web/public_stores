package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知（对应 messages 表）
 */
@Data
public class Message {

    private Long id;
    private Long userId;
    /** 发送者管理员 ID（系统自动消息为 null） */
    private Long senderId;
    /** 批次 ID：同一次管理员批量发送共享，用于管理端列表聚合 / 整批删除 */
    private Long batchId;
    /** 发送范围：ALL-全部用户，SEGMENT-分人群，SPECIFIED-指定用户（管理员发送；系统自动消息为 null） */
    private String scope;
    /** 分人群群发：筛选的卡类型 ID（scope=SEGMENT 时有值，仅作审计回溯） */
    private Long cardTypeId;
    /** 分人群群发：筛选的门店 ID（scope=SEGMENT 时有值，仅作审计回溯） */
    private Long storeId;
    /**
     * 类型：SYSTEM 系统公告 / ACTIVITY 活动 / VERIFY 核销成功 / EXPIRE 到期提醒 /
     * INTERACT 分享互动 / AI_PLAN AI计划 / COMPLAINT 投诉进度。
     * <p>
     * 新增取值必须同步前端映射，否则用户端只能显示为通用铃铛图标：
     * {@code vue3/src/api/messageAPI.ts} 的 type 联合类型、
     * {@code vue3/src/views/user/Notifications.vue} 的 typeIcon / typeClass / .tp-* 配色。
     */
    private String type;
    private String title;
    private String content;
    /** 关联业务 ID（如核销记录 ID / 分享 ID / 计划 ID 等） */
    private Long refId;
    /** 是否已读：0-未读，1-已读 */
    private Integer isRead;
    private LocalDateTime createdAt;

}
