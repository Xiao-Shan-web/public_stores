package com.xiaoshan.fitness.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店视图对象
 * <p>
 * 可直接作为 Redis 缓存值（store:list:all 缓存 List&lt;StoreVO&gt;），不暴露 isDeleted。
 */
@Data
public class StoreVO {

    /** 门店ID */
    private Long id;

    /** 门店名称 */
    private String name;

    /** 门店地址 */
    private String address;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
