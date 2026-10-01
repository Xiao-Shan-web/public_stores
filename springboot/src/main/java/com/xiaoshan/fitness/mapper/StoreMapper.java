package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.Store;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 门店 Mapper
 */
@Mapper
public interface StoreMapper {

    /**
     * 查询全部未删除门店（按创建时间升序）
     */
    @Select("SELECT id, name, address, is_deleted AS isDeleted, " +
            "created_at AS createdAt, updated_at AS updatedAt " +
            "FROM stores WHERE is_deleted = 0 ORDER BY created_at ASC")
    List<Store> findAll();

    /**
     * 根据ID查询门店（含软删除标记，便于判断存在性与引用校验）
     */
    @Select("SELECT id, name, address, is_deleted AS isDeleted, " +
            "created_at AS createdAt, updated_at AS updatedAt " +
            "FROM stores WHERE id = #{id}")
    Store findById(@Param("id") Long id);

    /**
     * 新增门店
     */
    @Insert("INSERT INTO stores(id, name, address, is_deleted) " +
            "VALUES(#{id}, #{name}, #{address}, 0)")
    int insert(Store store);

    /**
     * 编辑门店
     */
    @Update("UPDATE stores SET name = #{name}, address = #{address} " +
            "WHERE id = #{id} AND is_deleted = 0")
    int update(Store store);

    /**
     * 软删除门店
     */
    @Update("UPDATE stores SET is_deleted = 1 WHERE id = #{id}")
    int softDelete(@Param("id") Long id);

    /**
     * 统计绑定了该门店且未删除的卡类型数量（门店删除前的引用校验）
     */
    @Select("SELECT COUNT(*) FROM card_types WHERE store_id = #{storeId} AND is_deleted = 0")
    int countReferencingCardTypes(@Param("storeId") Long storeId);

}
