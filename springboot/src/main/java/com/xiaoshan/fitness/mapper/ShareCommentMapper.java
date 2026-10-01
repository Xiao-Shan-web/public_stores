package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.ShareComment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分享评论 Mapper
 */
@Mapper
public interface ShareCommentMapper {

    /**
     * 插入评论
     */
    @Insert("INSERT INTO share_comments(id, share_id, user_id, content) " +
            "VALUES(#{id}, #{shareId}, #{userId}, #{content})")
    int insert(ShareComment comment);

    /**
     * 查询某分享的评论列表（按时间倒序）：
     * JOIN users 带评论者手机号、JOIN user_profiles 带评论者昵称（均可能为空）
     */
    @Select("SELECT c.id, c.share_id AS shareId, c.user_id AS userId, c.content, " +
            "c.created_at AS createdAt, u.phone AS authorPhone, " +
            "up.nickname AS authorNickname, up.avatar AS authorAvatar, up.avatar_thumb AS authorAvatarThumb " +
            "FROM share_comments c " +
            "LEFT JOIN users u ON u.id = c.user_id " +
            "LEFT JOIN user_profiles up ON up.user_id = c.user_id " +
            "WHERE c.share_id = #{shareId} " +
            "ORDER BY c.created_at DESC LIMIT #{offset}, #{size}")
    List<ShareComment> findByShareId(@Param("shareId") Long shareId,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    /**
     * 查询某分享的评论总数
     */
    @Select("SELECT COUNT(*) FROM share_comments WHERE share_id = #{shareId}")
    long countByShareId(@Param("shareId") Long shareId);

    /**
     * 管理端删除单条评论（限定 shareId，防止跨分享误删）
     */
    @Delete("DELETE FROM share_comments WHERE id = #{id} AND share_id = #{shareId}")
    int deleteByIdAndShareId(@Param("id") Long id, @Param("shareId") Long shareId);

}
