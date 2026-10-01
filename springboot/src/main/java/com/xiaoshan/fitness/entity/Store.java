package com.xiaoshan.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店实体（对应 stores 表）
 * 卡类型可绑定单个门店（SINGLE_STORE）或标记为全店通用（ALL_STORE）
 */
@Data
public class Store {

    /** 门店ID（雪花算法生成） */
    private Long id;

    /** 门店名称 */
    private String name;

    /** 门店地址 */
    private String address;

    /** 是否删除：0-未删除，1-已删除（软删除标记） */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
