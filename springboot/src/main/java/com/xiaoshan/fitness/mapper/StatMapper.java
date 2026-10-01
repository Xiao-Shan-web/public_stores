package com.xiaoshan.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 数据统计 Mapper（只读聚合查询，无写入）
 * <p>
 * 统计口径（与前端展示文案保持一致）：
 * - 会员数：users 表注册用户（按 created_at）
 * - 有效会员卡：memberships.status = ACTIVE
 * - 核销：entry_records.result = 'SUCCESS'
 * - 营收：card_orders.status = 'PAID'，支付时间取 COALESCE(paid_at, pay_time, created_at)
 * - 门店分布：按核销卡关联的购卡门店快照（memberships.store_id），全店通用卡（NULL）单独归组
 * - 卡销量/收入：已支付订单（PAID），卡名称优先取 card_types.name，历史订单回退 card_orders.card_type
 */
@Mapper
public interface StatMapper {

    // ==================== 会员统计 ====================

    /**
     * 会员注册趋势（按天分组，仅返回有注册的日期，缺失日期由 Controller 补零）
     */
    @Select("SELECT DATE(created_at) AS d, COUNT(*) AS c FROM users " +
            "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(created_at)")
    List<Map<String, Object>> memberTrend(@Param("days") int days);

    /**
     * 会员卡状态分布（UNACTIVATED/ACTIVE/EXPIRED/DISABLED）
     */
    @Select("SELECT status, COUNT(*) AS c FROM memberships GROUP BY status")
    List<Map<String, Object>> membershipStatusCounts();

    /**
     * 未激活会员卡数
     */
    @Select("SELECT COUNT(*) FROM memberships WHERE status = 'UNACTIVATED'")
    long countUnactivated();

    /**
     * 7 天内到期的有效会员卡数
     */
    @Select("SELECT COUNT(*) FROM memberships " +
            "WHERE status = 'ACTIVE' AND end_time >= NOW() " +
            "AND end_time <= DATE_ADD(NOW(), INTERVAL 7 DAY)")
    long countExpiringSoon();

    // ==================== 核销统计 ====================

    /**
     * 核销趋势（按天分组，参数化天数）
     */
    @Select("SELECT DATE(created_at) AS d, COUNT(*) AS c FROM entry_records " +
            "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) AND result = 'SUCCESS' " +
            "GROUP BY DATE(created_at)")
    List<Map<String, Object>> entryTrend(@Param("days") int days);

    /**
     * 核销时段分布（0-23 时，仅成功核销）
     */
    @Select("SELECT HOUR(created_at) AS h, COUNT(*) AS c FROM entry_records " +
            "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) AND result = 'SUCCESS' " +
            "GROUP BY HOUR(created_at)")
    List<Map<String, Object>> entryByHour(@Param("days") int days);

    /**
     * 门店核销分布（按持卡购卡门店快照归组；全店通用卡记为"全店通用卡"）
     */
    @Select("SELECT COALESCE(s.name, '全店通用卡') AS name, COUNT(*) AS c " +
            "FROM entry_records er " +
            "LEFT JOIN memberships m ON m.id = er.membership_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE er.created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) AND er.result = 'SUCCESS' " +
            "GROUP BY COALESCE(s.name, '全店通用卡') " +
            "ORDER BY c DESC")
    List<Map<String, Object>> entryByStore(@Param("days") int days);

    // ==================== 营收统计 ====================

    /**
     * 营收趋势（按天分组：订单数 + 收入，仅 PAID）
     */
    @Select("SELECT DATE(COALESCE(paid_at, pay_time, created_at)) AS d, " +
            "       COUNT(*) AS orders, COALESCE(SUM(amount), 0) AS income " +
            "FROM card_orders " +
            "WHERE status = 'PAID' " +
            "AND COALESCE(paid_at, pay_time, created_at) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(COALESCE(paid_at, pay_time, created_at))")
    List<Map<String, Object>> incomeTrend(@Param("days") int days);

    /**
     * 累计总收入
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM card_orders WHERE status = 'PAID'")
    BigDecimal totalIncome();

    /**
     * 今日收入
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM card_orders " +
            "WHERE status = 'PAID' AND DATE(COALESCE(paid_at, pay_time, created_at)) = CURDATE()")
    BigDecimal todayIncome();

    /**
     * 本月收入（自然月）
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM card_orders " +
            "WHERE status = 'PAID' " +
            "AND COALESCE(paid_at, pay_time, created_at) >= DATE_FORMAT(CURDATE(), '%Y-%m-01')")
    BigDecimal monthIncome();

    /**
     * 已支付订单数
     */
    @Select("SELECT COUNT(*) FROM card_orders WHERE status = 'PAID'")
    long paidOrderCount();

    /**
     * 订单状态分布（PENDING/PAID/CANCELLED）
     */
    @Select("SELECT status, COUNT(*) AS c FROM card_orders GROUP BY status")
    List<Map<String, Object>> orderStatusCounts();

    // ==================== 会员卡（卡种）统计 ====================

    /**
     * 卡类型销量与收入排行（已支付订单口径，按销量降序）
     */
    @Select("SELECT COALESCE(ct.name, o.card_type) AS name, COUNT(*) AS sales, " +
            "       COALESCE(SUM(o.amount), 0) AS income " +
            "FROM card_orders o " +
            "LEFT JOIN card_types ct ON ct.id = o.card_type_id " +
            "WHERE o.status = 'PAID' " +
            "AND COALESCE(o.paid_at, o.pay_time, o.created_at) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY COALESCE(ct.name, o.card_type) " +
            "ORDER BY sales DESC")
    List<Map<String, Object>> cardSales(@Param("days") int days);

    // ==================== 会员活跃 / 留存 ====================

    /**
     * 活跃会员数（去重）：近 N 天内至少有 1 次成功核销的会员。
     * 口径：健身场景以"成功到店核销"为活跃事件（entry_records.result='SUCCESS'）。
     * days=1 即当日活跃 DAU，7/30 即周/月活跃口径。
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM entry_records " +
            "WHERE result = 'SUCCESS' AND user_id IS NOT NULL " +
            "AND created_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    long activeUsers(@Param("days") int days);

    /**
     * 各注册日的新会员数（cohort 分母）。
     * 仅统计已具备完整 7 天观察期的 cohort：注册于 [days 天前, 7 天前) 之间，
     * 避免"最近注册不足 7 天"的会员被算入分母导致留存率被系统性低估。
     */
    @Select("SELECT DATE(created_at) AS d, COUNT(*) AS c FROM users " +
            "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "AND created_at < DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(created_at)")
    List<Map<String, Object>> retentionCohortNew(@Param("days") int days);

    /**
     * 各注册日的 7 日留存会员数（cohort 分子）：
     * 注册后 7 天内（含注册当天，半开区间 [created_at, created_at+7d)）至少 1 次成功核销。
     */
    @Select("SELECT DATE(u.created_at) AS d, COUNT(DISTINCT u.id) AS c FROM users u " +
            "WHERE u.created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "AND u.created_at < DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "AND EXISTS (SELECT 1 FROM entry_records er " +
            "            WHERE er.user_id = u.id AND er.result = 'SUCCESS' " +
            "            AND er.created_at >= u.created_at " +
            "            AND er.created_at < DATE_ADD(u.created_at, INTERVAL 7 DAY)) " +
            "GROUP BY DATE(u.created_at)")
    List<Map<String, Object>> retentionCohortRetained(@Param("days") int days);

    // ==================== 会员卡续费 / 复购 ====================

    /**
     * 近 N 天续费单数与续费金额：
     * 一笔 PAID 订单若该会员在此之前已有更早的 PAID 订单，则计为续费单。
     */
    @Select("SELECT COUNT(*) AS orders, COALESCE(SUM(o.amount), 0) AS income " +
            "FROM card_orders o " +
            "WHERE o.status = 'PAID' " +
            "AND COALESCE(o.paid_at, o.pay_time, o.created_at) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "AND EXISTS (SELECT 1 FROM card_orders p " +
            "            WHERE p.user_id = o.user_id AND p.status = 'PAID' AND p.id <> o.id " +
            "            AND COALESCE(p.paid_at, p.pay_time, p.created_at) " +
            "                < COALESCE(o.paid_at, o.pay_time, o.created_at))")
    Map<String, Object> renewalStats(@Param("days") int days);

    /**
     * 近 N 天已支付订单总数（续费占比分母，订单口径）
     */
    @Select("SELECT COUNT(*) FROM card_orders " +
            "WHERE status = 'PAID' " +
            "AND COALESCE(paid_at, pay_time, created_at) >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)")
    long paidOrderCountInDays(@Param("days") int days);

    /**
     * 累计购卡会员数（至少 1 笔 PAID 订单的去重会员，复购率分母）
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM card_orders WHERE status = 'PAID'")
    long totalBuyers();

    /**
     * 复购会员数（累计 ≥2 笔 PAID 订单的去重会员，复购率分子）
     */
    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT user_id FROM card_orders WHERE status = 'PAID' " +
            "  GROUP BY user_id HAVING COUNT(*) >= 2" +
            ") t")
    long repeatBuyers();

    // ==================== Excel 导出数据源 ====================

    /**
     * 核销记录导出（近 N 天，含失败记录）
     */
    @Select("SELECT u.phone AS phone, " +
            "       m.card_no AS cardNo, " +
            "       COALESCE(ct.name, " +
            "           CASE m.card_type WHEN 'TIMES' THEN '次卡' WHEN 'MONTHLY' THEN '月卡' " +
            "               WHEN 'YEARLY' THEN '年卡' ELSE m.card_type END) AS cardName, " +
            "       DATE_FORMAT(er.created_at, '%Y-%m-%d %H:%i:%s') AS useTime, " +
            "       er.result AS result, " +
            "       er.fail_reason AS failReason " +
            "FROM entry_records er " +
            "LEFT JOIN users u ON er.user_id = u.id " +
            "LEFT JOIN memberships m ON er.membership_id = m.id " +
            "LEFT JOIN card_types ct ON m.card_type_id = ct.id " +
            "WHERE er.created_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "ORDER BY er.created_at DESC")
    List<Map<String, Object>> entriesForExport(@Param("days") int days);

    /**
     * 订单（营收明细）导出（近 N 天，含全部状态）
     */
    @Select("SELECT o.order_no AS orderNo, " +
            "       u.phone AS phone, " +
            "       COALESCE(ct.name, o.card_type) AS cardName, " +
            "       o.amount AS amount, " +
            "       o.status AS status, " +
            "       DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i:%s') AS createTime, " +
            "       CASE WHEN COALESCE(o.paid_at, o.pay_time) IS NULL THEN NULL " +
            "            ELSE DATE_FORMAT(COALESCE(o.paid_at, o.pay_time), '%Y-%m-%d %H:%i:%s') END AS payTime " +
            "FROM card_orders o " +
            "LEFT JOIN users u ON u.id = o.user_id " +
            "LEFT JOIN card_types ct ON ct.id = o.card_type_id " +
            "WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "ORDER BY o.created_at DESC")
    List<Map<String, Object>> ordersForExport(@Param("days") int days);

}
