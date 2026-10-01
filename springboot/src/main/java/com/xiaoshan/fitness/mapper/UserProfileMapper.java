package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.UserProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 用户资料 Mapper（每用户最多一条）
 */
@Mapper
public interface UserProfileMapper {

    /** 查询列片段（供各 SELECT 复用） */
    String COLUMNS = "user_id AS userId, avatar, avatar_thumb AS avatarThumb, " +
            "nickname, real_name AS realName, gender, birthday, bio, " +
            "created_at AS createdAt, updated_at AS updatedAt";

    /**
     * 根据用户ID查询资料
     */
    @Select("SELECT " + COLUMNS + " FROM user_profiles WHERE user_id = #{userId}")
    Optional<UserProfile> findByUserId(@Param("userId") Long userId);

    /**
     * 新增用户资料（首次保存时创建）
     */
    @Insert("INSERT INTO user_profiles (user_id, avatar, avatar_thumb, nickname, real_name, gender, birthday, bio) " +
            "VALUES (#{userId}, #{avatar}, #{avatarThumb}, #{nickname}, #{realName}, #{gender}, #{birthday}, #{bio})")
    int insert(UserProfile profile);

    /**
     * 全字段更新用户资料（PUT 表单整页提交）
     */
    @Update("UPDATE user_profiles SET avatar = #{avatar}, nickname = #{nickname}, " +
            "real_name = #{realName}, gender = #{gender}, birthday = #{birthday}, bio = #{bio} " +
            "WHERE user_id = #{userId}")
    int update(UserProfile profile);

    /**
     * 仅更新头像（头像上传成功后立即落库主图 + 缩略图）
     */
    @Update("UPDATE user_profiles SET avatar = #{avatar}, avatar_thumb = #{avatarThumb} WHERE user_id = #{userId}")
    int updateAvatar(@Param("userId") Long userId, @Param("avatar") String avatar,
                     @Param("avatarThumb") String avatarThumb);

}
