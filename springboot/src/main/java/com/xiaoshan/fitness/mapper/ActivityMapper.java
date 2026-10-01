package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.Activity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 限时活动 Mapper
 * 活动有效性：status=1 且 is_deleted=0 且 NOW() 落在 [start_time, end_time] 内
 */
@Mapper
public interface ActivityMapper {

    /** 进行中的活动（用户端列表，联表卡类型名称与价格） */
    @Select("SELECT a.id, a.title, a.subtitle, a.cover_url AS coverUrl, a.content, a.card_type_id AS cardTypeId, " +
            "       a.discount, a.quota_total AS quotaTotal, a.quota_used AS quotaUsed, " +
            "       a.start_time AS startTime, a.end_time AS endTime, " +
            "       ct.name AS cardTypeName, ct.price AS originalPrice, " +
            "       ROUND(ct.price * a.discount, 2) AS activityPrice " +
            "FROM activities a LEFT JOIN card_types ct ON ct.id = a.card_type_id " +
            "WHERE a.status = 1 AND a.is_deleted = 0 " +
            "AND NOW() BETWEEN a.start_time AND a.end_time " +
            "AND (a.quota_total IS NULL OR a.quota_used < a.quota_total) " +
            "ORDER BY a.end_time ASC")
    List<Map<String, Object>> findOngoing();

    /** 管理端：全部活动（含未开始/已结束/下架） */
    @Select("SELECT a.id, a.title, a.subtitle, a.cover_url AS coverUrl, a.content, a.card_type_id AS cardTypeId, " +
            "       a.discount, a.quota_total AS quotaTotal, a.quota_used AS quotaUsed, " +
            "       a.start_time AS startTime, a.end_time AS endTime, CAST(a.status AS SIGNED) AS status, " +
            "       CAST(a.is_deleted AS SIGNED) AS isDeleted, " +
            "       a.created_at AS createdAt, ct.name AS cardTypeName, ct.price AS originalPrice, " +
            "       CASE WHEN NOW() BETWEEN a.start_time AND a.end_time THEN 'ONGOING' " +
            "            WHEN NOW() < a.start_time THEN 'UPCOMING' ELSE 'ENDED' END AS phase, " +
            // 用户端是否可见：口径与 findOngoing 完全一致（上架 + 时间窗内 + 名额未满）
            "       CAST((a.status = 1 AND NOW() BETWEEN a.start_time AND a.end_time " +
            "            AND (a.quota_total IS NULL OR a.quota_used < a.quota_total)) AS SIGNED) AS userVisible " +
            "FROM activities a LEFT JOIN card_types ct ON ct.id = a.card_type_id " +
            "WHERE a.is_deleted = 0 ORDER BY a.created_at DESC")
    List<Map<String, Object>> findAllForAdmin();

    /** 活动详情（联表卡类型） */
    @Select("SELECT a.id, a.title, a.subtitle, a.cover_url AS coverUrl, a.content, a.card_type_id AS cardTypeId, " +
            "       a.discount, a.quota_total AS quotaTotal, a.quota_used AS quotaUsed, " +
            "       a.start_time AS startTime, a.end_time AS endTime, CAST(a.status AS SIGNED) AS status, " +
            "       ct.name AS cardTypeName, ct.price AS originalPrice, " +
            "       ROUND(ct.price * a.discount, 2) AS activityPrice " +
            "FROM activities a LEFT JOIN card_types ct ON ct.id = a.card_type_id WHERE a.id = #{id}")
    Map<String, Object> findDetail(@Param("id") Long id);

    /** 查询某卡类型当前进行中的活动（下单时取活动价用，同卡多活动取折扣最低者） */
    @Select("SELECT id, title, card_type_id AS cardTypeId, discount, quota_total AS quotaTotal, " +
            "       quota_used AS quotaUsed, start_time AS startTime, end_time AS endTime " +
            "FROM activities " +
            "WHERE card_type_id = #{cardTypeId} AND status = 1 AND is_deleted = 0 " +
            "AND NOW() BETWEEN start_time AND end_time " +
            "AND (quota_total IS NULL OR quota_used < quota_total) " +
            "ORDER BY discount ASC LIMIT 1")
    Activity findOngoingByCardType(@Param("cardTypeId") Long cardTypeId);

    /** 新建活动 */
    @Insert("INSERT INTO activities(id, title, subtitle, cover_url, content, card_type_id, discount, " +
            "quota_total, quota_used, start_time, end_time, status, is_deleted) " +
            "VALUES(#{id}, #{title}, #{subtitle}, #{coverUrl}, #{content}, #{cardTypeId}, #{discount}, " +
            "#{quotaTotal}, 0, #{startTime}, #{endTime}, #{status}, 0)")
    int insert(Activity activity);

    /** 更新活动 */
    @Update("UPDATE activities SET title = #{title}, subtitle = #{subtitle}, cover_url = #{coverUrl}, " +
            "content = #{content}, card_type_id = #{cardTypeId}, discount = #{discount}, " +
            "quota_total = #{quotaTotal}, start_time = #{startTime}, end_time = #{endTime}, status = #{status} " +
            "WHERE id = #{id} AND is_deleted = 0")
    int update(Activity activity);

    /** 上下架 */
    @Update("UPDATE activities SET status = #{status} WHERE id = #{id} AND is_deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 软删除 */
    @Update("UPDATE activities SET is_deleted = 1, status = 0 WHERE id = #{id} AND is_deleted = 0")
    int softDelete(@Param("id") Long id);

    /**
     * 占用活动名额（限时限量：仅当未超额时成功）
     */
    @Update("UPDATE activities SET quota_used = quota_used + 1 " +
            "WHERE id = #{id} AND status = 1 AND is_deleted = 0 " +
            "AND NOW() BETWEEN start_time AND end_time " +
            "AND (quota_total IS NULL OR quota_used < quota_total)")
    int consumeQuota(@Param("id") Long id);

}
