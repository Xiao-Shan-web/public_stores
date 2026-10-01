package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.ShareLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * 分享点赞 Mapper
 */
@Mapper
public interface ShareLikeMapper {

    /**
     * 插入点赞
     */
    @Insert("INSERT INTO share_likes(id, share_id, user_id) " +
            "VALUES(#{id}, #{shareId}, #{userId})")
    int insert(ShareLike like);

    /**
     * 删除点赞
     */
    @Delete("DELETE FROM share_likes WHERE share_id = #{shareId} AND user_id = #{userId}")
    int delete(@Param("shareId") Long shareId, @Param("userId") Long userId);

    /**
     * 查询是否已点赞
     */
    @Select("SELECT COUNT(*) FROM share_likes WHERE share_id = #{shareId} AND user_id = #{userId}")
    int exists(@Param("shareId") Long shareId, @Param("userId") Long userId);

}
