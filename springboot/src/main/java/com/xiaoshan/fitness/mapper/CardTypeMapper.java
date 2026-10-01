package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.CardType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 健身卡类型 Mapper
 */
@Mapper
public interface CardTypeMapper {

    /**
     * 查询全部卡类型（排除已软删除，按创建时间升序，含分类/适用范围/绑定门店/节数）
     */
    @Select("SELECT ct.id, ct.name, ct.category, ct.scope, ct.store_id AS storeId, s.name AS storeName, " +
            "ct.duration_days AS durationDays, ct.total_times AS totalTimes, ct.price, ct.description, " +
            "ct.is_active AS isActive, ct.created_at AS createdAt, ct.updated_at AS updatedAt " +
            "FROM card_types ct " +
            "LEFT JOIN stores s ON s.id = ct.store_id AND s.is_deleted = 0 " +
            "WHERE ct.is_deleted = 0 ORDER BY ct.created_at ASC")
    List<CardType> findAll();

    /**
     * 查询启用的卡类型（用户端可用列表，排除已软删除与禁用）
     * 含全部分类（NORMAL/PT），用于需要展示分类的页面
     */
    @Select("SELECT ct.id, ct.name, ct.category, ct.scope, ct.store_id AS storeId, s.name AS storeName, " +
            "ct.duration_days AS durationDays, ct.total_times AS totalTimes, ct.price, ct.description, " +
            "ct.is_active AS isActive, ct.created_at AS createdAt, ct.updated_at AS updatedAt " +
            "FROM card_types ct " +
            "LEFT JOIN stores s ON s.id = ct.store_id AND s.is_deleted = 0 " +
            "WHERE ct.is_deleted = 0 AND ct.is_active = 1 ORDER BY ct.created_at ASC")
    List<CardType> findAllActive();

    /**
     * 根据ID查询卡类型（含软删除标记，便于判断存在性）
     */
    @Select("SELECT ct.id, ct.name, ct.category, ct.scope, ct.store_id AS storeId, s.name AS storeName, " +
            "ct.duration_days AS durationDays, ct.total_times AS totalTimes, ct.price, ct.description, " +
            "ct.is_active AS isActive, ct.is_deleted AS isDeleted, " +
            "ct.created_at AS createdAt, ct.updated_at AS updatedAt " +
            "FROM card_types ct " +
            "LEFT JOIN stores s ON s.id = ct.store_id AND s.is_deleted = 0 " +
            "WHERE ct.id = #{id}")
    CardType findById(@Param("id") Long id);

    /**
     * 新建卡类型（category 默认 NORMAL，可由管理端指定 PT；scope/store_id/total_times 由上层校验）
     */
    @Insert("INSERT INTO card_types(id, name, category, scope, store_id, duration_days, total_times, " +
            "price, description, is_active, is_deleted) " +
            "VALUES(#{id}, #{name}, #{category}, #{scope}, #{storeId}, #{durationDays}, #{totalTimes}, " +
            "#{price}, #{description}, #{isActive}, 0)")
    int insert(CardType cardType);

    /**
     * 编辑卡类型（名称、分类、适用范围、门店、有效天数、节数、价格、描述、启用状态）
     */
    @Update("UPDATE card_types SET name = #{name}, category = #{category}, scope = #{scope}, " +
            "store_id = #{storeId}, duration_days = #{durationDays}, total_times = #{totalTimes}, " +
            "price = #{price}, description = #{description}, is_active = #{isActive} " +
            "WHERE id = #{id} AND is_deleted = 0")
    int update(CardType cardType);

    /**
     * 切换启用状态
     */
    @Update("UPDATE card_types SET is_active = #{isActive} WHERE id = #{id} AND is_deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("isActive") Integer isActive);

    /**
     * 软删除（is_deleted = 1）
     */
    @Update("UPDATE card_types SET is_deleted = 1 WHERE id = #{id}")
    int softDelete(@Param("id") Long id);

    /**
     * 按 名称+适用范围+门店 统计未删除的卡类型数量（同范围同名唯一校验，不同门店可同名）
     */
    @Select("SELECT COUNT(*) FROM card_types " +
            "WHERE name = #{name} AND scope = #{scope} " +
            "AND IFNULL(store_id, 0) = IFNULL(#{storeId}, 0) AND is_deleted = 0")
    int countByNameScopeStore(@Param("name") String name,
                              @Param("scope") String scope,
                              @Param("storeId") Long storeId);

    /**
     * 同上但排除指定ID（编辑时唯一校验）
     */
    @Select("SELECT COUNT(*) FROM card_types " +
            "WHERE name = #{name} AND scope = #{scope} " +
            "AND IFNULL(store_id, 0) = IFNULL(#{storeId}, 0) AND is_deleted = 0 AND id <> #{id}")
    int countByNameScopeStoreExcludeId(@Param("name") String name,
                                       @Param("scope") String scope,
                                       @Param("storeId") Long storeId,
                                       @Param("id") Long id);

}
