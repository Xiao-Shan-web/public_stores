package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.Share;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

/**
 * 会员分享 Mapper
 */
@Mapper
public interface ShareMapper {

    /** 公共查询列（列表/详情/我的共用，新增列 images/view_count 保持同步） */
    String SHARE_COLUMNS =
            "s.id, s.user_id AS userId, s.title, s.content, s.video_url AS videoUrl, " +
            "s.cover_url AS coverUrl, s.images, s.like_count AS likeCount, " +
            "s.comment_count AS commentCount, s.view_count AS viewCount, " +
            "s.status, s.created_at AS createdAt, s.updated_at AS updatedAt, " +
            "u.phone AS authorPhone, up.avatar AS authorAvatar, up.avatar_thumb AS authorAvatarThumb ";

    /** 公共 JOIN：users 取手机号、user_profiles 取头像（均 LEFT JOIN 避免无资料用户丢失记录） */
    String SHARE_JOINS =
            "LEFT JOIN users u ON u.id = s.user_id " +
            "LEFT JOIN user_profiles up ON up.user_id = s.user_id ";

    /**
     * 分页查询分享列表（按创建时间倒序，JOIN users 带作者手机号、user_profiles 带头像）
     */
    @Select("SELECT " + SHARE_COLUMNS +
            "FROM shares s " + SHARE_JOINS +
            "WHERE s.status = 'NORMAL' " +
            "ORDER BY s.created_at DESC LIMIT #{offset}, #{size}")
    List<Share> findPage(@Param("offset") int offset, @Param("size") int size);

    /**
     * 查询总数
     */
    @Select("SELECT COUNT(*) FROM shares WHERE status = 'NORMAL'")
    long countAll();

    /**
     * 根据ID查询分享（带作者手机号、头像）
     */
    @Select("SELECT " + SHARE_COLUMNS +
            "FROM shares s " + SHARE_JOINS +
            "WHERE s.id = #{id}")
    Share findById(@Param("id") Long id);

    /**
     * 查询当前用户是否已点赞该分享（用于列表填充 liked 字段）
     */
    @Select("SELECT COUNT(*) FROM share_likes WHERE share_id = #{shareId} AND user_id = #{userId}")
    int countLikeByUser(@Param("shareId") Long shareId, @Param("userId") Long userId);

    /**
     * 查询某用户的分享列表
     */
    @Select("SELECT " + SHARE_COLUMNS +
            "FROM shares s " + SHARE_JOINS +
            "WHERE s.user_id = #{userId} AND s.status = 'NORMAL' " +
            "ORDER BY s.created_at DESC LIMIT #{offset}, #{size}")
    List<Share> findPageByUserId(@Param("userId") Long userId,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    /**
     * 查询某用户的分享总数
     */
    @Select("SELECT COUNT(*) FROM shares WHERE user_id = #{userId} AND status = 'NORMAL'")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 插入分享
     */
    @Insert("INSERT INTO shares(id, user_id, title, content, video_url, cover_url, images, status) " +
            "VALUES(#{id}, #{userId}, #{title}, #{content}, #{videoUrl}, #{coverUrl}, #{images}, 'NORMAL')")
    @Options(useGeneratedKeys = false)
    int insert(Share share);

    /**
     * 点赞数 +1
     */
    @Update("UPDATE shares SET like_count = like_count + 1 WHERE id = #{id}")
    int incrLikeCount(@Param("id") Long id);

    /**
     * 点赞数 -1（不低于0）
     */
    @Update("UPDATE shares SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{id}")
    int decrLikeCount(@Param("id") Long id);

    /**
     * 评论数 +1
     */
    @Update("UPDATE shares SET comment_count = comment_count + 1 WHERE id = #{id}")
    int incrCommentCount(@Param("id") Long id);

    /**
     * 评论数 -1（不低于0，管理端删除评论时同步）
     */
    @Update("UPDATE shares SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = #{id}")
    int decrCommentCount(@Param("id") Long id);

    /**
     * 浏览数 +1
     */
    @Update("UPDATE shares SET view_count = view_count + 1 WHERE id = #{id}")
    int incrViewCount(@Param("id") Long id);

    // ==================== 管理端（内容审核） ====================

    /**
     * 管理端分页查询：不过滤状态（NORMAL/HIDDEN 都能看到），支持状态与关键字筛选。
     * status 为 null → 全部状态；keyword 为 null → 不筛选（标题/正文/作者手机号模糊匹配）。
     */
    @Select("SELECT " + SHARE_COLUMNS +
            "FROM shares s " + SHARE_JOINS +
            "WHERE (#{status} IS NULL OR s.status = #{status}) " +
            "AND (#{keyword} IS NULL OR s.title LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR s.content LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR u.phone LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY s.created_at DESC LIMIT #{offset}, #{size}")
    List<Share> findAdminPage(@Param("status") String status,
                              @Param("keyword") String keyword,
                              @Param("offset") int offset,
                              @Param("size") int size);

    /**
     * 管理端查询总数（筛选条件与 findAdminPage 保持一致）
     */
    @Select("SELECT COUNT(*) FROM shares s LEFT JOIN users u ON u.id = s.user_id " +
            "WHERE (#{status} IS NULL OR s.status = #{status}) " +
            "AND (#{keyword} IS NULL OR s.title LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR s.content LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR u.phone LIKE CONCAT('%', #{keyword}, '%'))")
    long countAdmin(@Param("status") String status, @Param("keyword") String keyword);

    /**
     * 按状态统计条数（管理端「全部/正常/已隐藏」筛选徽标）
     */
    @Select("SELECT COUNT(*) FROM shares WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 更新状态：NORMAL-正常（用户端可见），HIDDEN-隐藏（用户端不可见）
     */
    @Update("UPDATE shares SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 删除分享（管理端违规内容下架，硬删）
     */
    @Delete("DELETE FROM shares WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 删除某分享下的全部评论（删除分享时连带清理，避免产生孤儿数据）
     */
    @Delete("DELETE FROM share_comments WHERE share_id = #{shareId}")
    int deleteCommentsByShareId(@Param("shareId") Long shareId);

    /**
     * 删除某分享下的全部点赞记录（删除分享时连带清理）
     */
    @Delete("DELETE FROM share_likes WHERE share_id = #{shareId}")
    int deleteLikesByShareId(@Param("shareId") Long shareId);

}
