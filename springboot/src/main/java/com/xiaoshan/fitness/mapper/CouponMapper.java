package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.Coupon;
import com.xiaoshan.fitness.entity.UserCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 优惠券 Mapper（模板 + 用户券）
 * <p>
 * 并发要点：
 * - 领取限量：{@link #incrIssuedCount} 带 `issued_count < total_count` 条件，靠 DB 行锁保证不超发
 * - 状态流转：{@link #lockForOrder} / {@link #markUsed} / {@link #releaseLock} 均带状态前置条件，
 *   返回 0 行表示状态已被其他请求改变（防重复使用）
 */
@Mapper
public interface CouponMapper {

    // ==================== 券模板 ====================

    String COUPON_COLUMNS = "id, name, type, threshold, amount, discount, max_discount AS maxDiscount, " +
            "total_count AS totalCount, issued_count AS issuedCount, " +
            "per_user_limit AS perUserLimit, valid_type AS validType, start_time AS startTime, end_time AS endTime, " +
            "valid_days AS validDays, status, is_deleted AS isDeleted, created_at AS createdAt, updated_at AS updatedAt ";

    /** 上架可领的券模板列表（不限量或未领完） */
    @Select("SELECT " + COUPON_COLUMNS +
            "FROM coupons WHERE status = 1 AND is_deleted = 0 " +
            "AND (total_count = 0 OR issued_count < total_count) " +
            "ORDER BY amount DESC")
    List<Coupon> findAvailable();

    /** 管理端：全部券模板（含下架） */
    @Select("SELECT " + COUPON_COLUMNS +
            "FROM coupons WHERE is_deleted = 0 ORDER BY created_at DESC")
    List<Coupon> findAll();

    /** 按ID查券模板 */
    @Select("SELECT " + COUPON_COLUMNS + "FROM coupons WHERE id = #{id}")
    Coupon findById(@Param("id") Long id);

    /** 新建券模板 */
    @Insert("INSERT INTO coupons(id, name, type, threshold, amount, discount, max_discount, total_count, issued_count, per_user_limit, " +
            "valid_type, start_time, end_time, valid_days, status, is_deleted) " +
            "VALUES(#{id}, #{name}, #{type}, #{threshold}, #{amount}, #{discount}, #{maxDiscount}, #{totalCount}, 0, #{perUserLimit}, " +
            "#{validType}, #{startTime}, #{endTime}, #{validDays}, #{status}, 0)")
    int insert(Coupon coupon);

    /** 更新券模板（仅可改名称/门槛/面额/折扣/总量/限领/有效期/状态） */
    @Update("UPDATE coupons SET name = #{name}, type = #{type}, threshold = #{threshold}, amount = #{amount}, " +
            "discount = #{discount}, max_discount = #{maxDiscount}, " +
            "total_count = #{totalCount}, per_user_limit = #{perUserLimit}, valid_type = #{validType}, " +
            "start_time = #{startTime}, end_time = #{endTime}, valid_days = #{validDays}, status = #{status} " +
            "WHERE id = #{id} AND is_deleted = 0")
    int update(Coupon coupon);

    /** 上下架 */
    @Update("UPDATE coupons SET status = #{status} WHERE id = #{id} AND is_deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 软删除 */
    @Update("UPDATE coupons SET is_deleted = 1, status = 0 WHERE id = #{id} AND is_deleted = 0")
    int softDelete(@Param("id") Long id);

    /**
     * 领取计数 +1（乐观并发：仅当未超发时才成功）
     * total_count = 0 表示不限量，直接累加
     */
    @Update("UPDATE coupons SET issued_count = issued_count + 1 " +
            "WHERE id = #{id} AND is_deleted = 0 AND status = 1 " +
            "AND (total_count = 0 OR issued_count < total_count)")
    int incrIssuedCount(@Param("id") Long id);

    // ==================== 用户券 ====================

    /** 发放用户券 */
    @Insert("INSERT INTO user_coupons(id, coupon_id, user_id, status, order_id, start_time, end_time) " +
            "VALUES(#{id}, #{couponId}, #{userId}, #{status}, NULL, #{startTime}, #{endTime})")
    int insertUserCoupon(UserCoupon uc);

    /** 统计用户已领取某券的数量（含已使用，用于每人限领校验） */
    @Select("SELECT COUNT(*) FROM user_coupons WHERE coupon_id = #{couponId} AND user_id = #{userId}")
    long countUserCoupon(@Param("couponId") Long couponId, @Param("userId") Long userId);

    /** 用户券详情（含券模板信息，用于校验门槛/面额/折扣） */
    @Select("SELECT uc.id, uc.coupon_id AS couponId, uc.user_id AS userId, uc.status, uc.order_id AS orderId, " +
            "uc.start_time AS startTime, uc.end_time AS endTime, uc.used_at AS usedAt, uc.created_at AS createdAt, " +
            "c.name AS couponName, c.type AS couponType, c.threshold, c.amount, " +
            "c.discount, c.max_discount AS maxDiscount " +
            "FROM user_coupons uc LEFT JOIN coupons c ON c.id = uc.coupon_id WHERE uc.id = #{id}")
    Map<String, Object> findUserCouponDetail(@Param("id") Long id);

    /**
     * 用户券列表（联表券模板名称/面额），
     * scope: UNUSED-可用 / LOCKED-已占用 / USED-已使用 / EXPIRED-已过期 / ALL-全部
     * 可用口径：status=UNUSED 且未过期；LOCKED 联表带出占用订单号，便于前端展示「占用中」及释放入口。
     */
    @Select("<script>" +
            "SELECT uc.id, uc.coupon_id AS couponId, uc.status, uc.order_id AS orderId, " +
            "o.order_no AS orderNo, o.status AS orderStatus, " +
            "uc.start_time AS startTime, uc.end_time AS endTime, uc.used_at AS usedAt, uc.created_at AS createdAt, " +
            "c.name AS couponName, c.type AS couponType, c.threshold, c.amount, c.discount, " +
            "c.max_discount AS maxDiscount, " +
            "CASE WHEN uc.status IN ('UNUSED', 'LOCKED') AND uc.end_time &lt; NOW() THEN 1 ELSE 0 END AS expired " +
            "FROM user_coupons uc LEFT JOIN coupons c ON c.id = uc.coupon_id " +
            "LEFT JOIN card_orders o ON o.id = uc.order_id " +
            "WHERE uc.user_id = #{userId} " +
            "<if test=\"scope == 'UNUSED'\">AND uc.status = 'UNUSED' AND uc.end_time &gt;= NOW() </if>" +
            "<if test=\"scope == 'LOCKED'\">AND uc.status = 'LOCKED' </if>" +
            "<if test=\"scope == 'USED'\">AND uc.status = 'USED' </if>" +
            "<if test=\"scope == 'EXPIRED'\">AND (uc.status = 'EXPIRED' OR (uc.status IN ('UNUSED', 'LOCKED') AND uc.end_time &lt; NOW())) </if>" +
            "ORDER BY uc.status = 'UNUSED' DESC, uc.end_time ASC" +
            "</script>")
    List<Map<String, Object>> findUserCoupons(@Param("userId") Long userId, @Param("scope") String scope);

    /** 统计用户可用券数量（个人中心角标） */
    @Select("SELECT COUNT(*) FROM user_coupons WHERE user_id = #{userId} " +
            "AND status = 'UNUSED' AND end_time >= NOW()")
    long countUsable(@Param("userId") Long userId);

    /**
     * 下单锁定券：UNUSED → LOCKED（带状态前置条件防重复占用）
     */
    @Update("UPDATE user_coupons SET status = 'LOCKED', order_id = #{orderId} " +
            "WHERE id = #{id} AND user_id = #{userId} AND status = 'UNUSED' AND end_time >= NOW()")
    int lockForOrder(@Param("id") Long id, @Param("userId") Long userId, @Param("orderId") Long orderId);

    /**
     * 支付成功核销券：LOCKED → USED
     */
    @Update("UPDATE user_coupons SET status = 'USED', used_at = NOW() " +
            "WHERE id = #{id} AND status = 'LOCKED' AND order_id = #{orderId}")
    int markUsed(@Param("id") Long id, @Param("orderId") Long orderId);

    /**
     * 订单取消/超时释放券：LOCKED → UNUSED（仅当券未过期时归还）
     */
    @Update("UPDATE user_coupons SET status = 'UNUSED', order_id = NULL " +
            "WHERE id = #{id} AND status = 'LOCKED' AND order_id = #{orderId} " +
            "AND end_time >= NOW()")
    int releaseLock(@Param("id") Long id, @Param("orderId") Long orderId);

    /**
     * 订单取消时券已过期：LOCKED → EXPIRED（不再归还用户）
     */
    @Update("UPDATE user_coupons SET status = 'EXPIRED' " +
            "WHERE id = #{id} AND status = 'LOCKED' AND order_id = #{orderId} AND end_time < NOW()")
    int expireLocked(@Param("id") Long id, @Param("orderId") Long orderId);

    /**
     * 释放锁定券（不校验订单号，按是否过期一次性定状态）：
     * 未过期 → UNUSED，已过期 → EXPIRED，并清空 order_id。
     * 用于 order_id 缺失等异常锁定券的兜底回收。
     */
    @Update("UPDATE user_coupons SET status = CASE WHEN end_time >= NOW() THEN 'UNUSED' ELSE 'EXPIRED' END, " +
            "order_id = NULL WHERE id = #{id} AND status = 'LOCKED'")
    int releaseLockById(@Param("id") Long id);

    /**
     * 退款退回券：USED → UNUSED（仅当券未过期），清空 order_id 与核销时间。
     * 业务规则：退款时优惠券若仍在有效期内则退回用户，已过期则作废。
     */
    @Update("UPDATE user_coupons SET status = 'UNUSED', order_id = NULL, used_at = NULL " +
            "WHERE id = #{id} AND status = 'USED' AND order_id = #{orderId} AND end_time >= NOW()")
    int releaseUsed(@Param("id") Long id, @Param("orderId") Long orderId);

    /**
     * 退款时券已过期：USED → EXPIRED（作废，不退回）
     */
    @Update("UPDATE user_coupons SET status = 'EXPIRED', used_at = NULL " +
            "WHERE id = #{id} AND status = 'USED' AND order_id = #{orderId} AND end_time < NOW()")
    int expireUsed(@Param("id") Long id, @Param("orderId") Long orderId);

    /**
     * 兜底：查出处于 LOCKED 但关联订单已不是 PENDING（订单已支付/取消/退款）或订单缺失的券。
     * 这类券是"状态联动"失败的残留，由定时任务按订单终态补偿处理，避免永久占用。
     */
    @Select("SELECT uc.id AS userCouponId, uc.order_id AS orderId, o.status AS orderStatus " +
            "FROM user_coupons uc LEFT JOIN card_orders o ON o.id = uc.order_id " +
            "WHERE uc.status = 'LOCKED' AND (o.id IS NULL OR o.status != 'PENDING') " +
            "ORDER BY uc.created_at ASC LIMIT 200")
    List<Map<String, Object>> findOrphanLockedCoupons();

    /** 管理端：券领取/使用统计概览 */
    @Select("SELECT c.id AS couponId, c.name AS name, c.total_count AS totalCount, c.issued_count AS issuedCount, " +
            "       COALESCE(SUM(CASE WHEN uc.status = 'USED' THEN 1 ELSE 0 END), 0) AS usedCount " +
            "FROM coupons c LEFT JOIN user_coupons uc ON uc.coupon_id = c.id " +
            "WHERE c.is_deleted = 0 GROUP BY c.id, c.name, c.total_count, c.issued_count " +
            "ORDER BY c.created_at DESC")
    List<Map<String, Object>> couponStats();

}
