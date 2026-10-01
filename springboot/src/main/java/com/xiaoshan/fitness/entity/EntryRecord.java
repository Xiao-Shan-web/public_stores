package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 核销记录实体（对应 entry_records 表）
 * 刷脸核销无论成功或失败都写入一条记录；核销时间取 created_at。
 */
@Data
public class EntryRecord {

    /** 核销记录ID（雪花算法生成） */
    private Long id;

    /** 会员用户ID（未识别到人脸时为空） */
    private Long userId;

    /** 会员卡ID（未匹配到会员卡时为空） */
    private Long membershipId;

    /** 核销方式：FACE-刷脸核销 */
    private String checkType;

    /** 核销结果：SUCCESS-入场成功，FAILED-入场失败 */
    private String result;

    /** 失败原因（result=FAILED 时填写） */
    private String failReason;

    /** 核销时间（即创建时间） */
    private LocalDateTime createdAt;

}
