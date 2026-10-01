package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 管理员 Mapper
 */
@Mapper
public interface AdminMapper {

    /**
     * 根据ID查询管理员
     */
    @Select("SELECT id, username, password, created_at, updated_at " +
            "FROM admins WHERE id = #{id}")
    Optional<Admin> findById(@Param("id") Long id);

    /**
     * 根据账号查询管理员（登录用）
     */
    @Select("SELECT id, username, password, created_at, updated_at " +
            "FROM admins WHERE username = #{username}")
    Optional<Admin> findByUsername(@Param("username") String username);

}
