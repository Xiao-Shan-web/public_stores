package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.EntryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 核销记录 Mapper
 * 关联 users（会员手机号）、memberships（卡号）、card_types（卡名称）。
 * 核销时间统一取 created_at，列表用 DATE_FORMAT 输出为 yyyy-MM-dd HH:mm:ss（不带 T）。
 */
@Mapper
public interface EntryRecordMapper {

    /**
     * 插入核销记录（成功/失败都写；created_at 由数据库默认当前时间）
     */
    @Insert("INSERT INTO entry_records(id, user_id, membership_id, check_type, result, fail_reason) " +
            "VALUES(#{id}, #{userId}, #{membershipId}, #{checkType}, #{result}, #{failReason})")
    int insert(EntryRecord entryRecord);

    /**
     * 分页查询用户核销记录（核销时间 / 卡名称 / 核销结果 / 失败原因）
     */
    @Select("SELECT er.id AS id, " +
            "       DATE_FORMAT(er.created_at, '%Y-%m-%d %H:%i:%s') AS useTime, " +
            "       COALESCE(ct.name, " +
            "           CASE m.card_type WHEN 'TIMES' THEN '次卡' WHEN 'MONTHLY' THEN '月卡' " +
            "               WHEN 'YEARLY' THEN '年卡' ELSE m.card_type END) AS cardName, " +
            "       er.result AS result, " +
            "       er.fail_reason AS failReason " +
            "FROM entry_records er " +
            "LEFT JOIN memberships m ON er.membership_id = m.id " +
            "LEFT JOIN card_types ct ON m.card_type_id = ct.id " +
            "WHERE er.user_id = #{userId} " +
            "ORDER BY er.created_at DESC " +
            "LIMIT #{offset}, #{size}")
    List<Map<String, Object>> findByUserId(@Param("userId") Long userId,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    /**
     * 统计用户核销记录总数
     */
    @Select("SELECT COUNT(*) FROM entry_records WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 查询全部核销记录（会员手机号 / 卡号 / 卡名称 / 核销时间 / 核销结果 / 失败原因），按时间倒序
     */
    @Select("SELECT er.id AS id, " +
            "       u.phone AS phone, " +
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
            "ORDER BY er.created_at DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> findAll(@Param("limit") int limit);

    /**
     * 统计今日成功核销数
     */
    @Select("SELECT COUNT(*) FROM entry_records WHERE DATE(created_at) = CURDATE() AND result = 'SUCCESS'")
    long countToday();

    /**
     * 近 7 天成功核销趋势（按日期分组，仅返回有记录的日期）
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS count " +
            "FROM entry_records " +
            "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) AND result = 'SUCCESS' " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date")
    List<Map<String, Object>> trend7Days();

}
