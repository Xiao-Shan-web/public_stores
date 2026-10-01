package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * 根据ID查询用户
     */
    @Select("SELECT id, phone, app_id, status, created_at, updated_at " +
            "FROM users WHERE id = #{id}")
    Optional<User> findById(@Param("id") Long id);

    /**
     * 根据手机号查询用户（登录用）
     */
    @Select("SELECT id, phone, app_id, status, created_at, updated_at " +
            "FROM users WHERE phone = #{phone}")
    Optional<User> findByPhone(@Param("phone") String phone);

    /**
     * 查询全部用户ID（系统通知群发用）
     */
    @Select("SELECT id FROM users")
    List<Long> findAllIds();

    /**
     * 新增用户（一键登录自动创建）
     */
    @Insert("INSERT INTO users (id, phone, app_id) " +
            "VALUES (#{id}, #{phone}, #{appId})")
    int insert(User user);

    /**
     * 管理端：分页查询用户列表（关联当前代表性会员卡），按注册时间倒序。
     * 「会员卡」字段口径 = 当前生效卡，非最新购买的卡：
     * 1. 有 ACTIVE 卡 → 取最新一张 ACTIVE（显示卡类型）
     * 2. 无 ACTIVE 但有 UNACTIVATED → 取最新一张 UNACTIVATED（前端显示「未激活」）
     * 3. 都没有 → 不关联（cardStatus 为 NULL，前端显示「无」）
     * 避免「已生效月卡 + 新购未激活季卡」被未激活卡覆盖。
     */
    @Select("SELECT u.id AS id, " +
            "       u.phone AS phone, " +
            "       u.phone AS nickname, " +
            "       up.avatar AS avatar, up.avatar_thumb AS avatarThumb, " +
            "       COALESCE(ct.name, " +
            "           CASE m.card_type WHEN 'TIMES' THEN '次卡' " +
            "               WHEN 'MONTHLY' THEN '月卡' WHEN 'YEARLY' THEN '年卡' " +
            "               ELSE m.card_type END) AS cardName, " +
            "       m.id AS cardId, " +
            "       m.remaining_times AS remainTimes, " +
            "       m.end_time AS expireTime, " +
            "       m.status AS cardStatus, " +
            "       CAST(u.status AS SIGNED) AS userStatus, " +
            "       u.created_at AS createTime, " +
            "       CAST((ff.id IS NOT NULL) AS SIGNED) AS faceRegistered " +
            "FROM users u " +
            "LEFT JOIN user_profiles up ON up.user_id = u.id " +
            "LEFT JOIN memberships m ON m.id = " +
            "   (SELECT id FROM memberships " +
            "    WHERE user_id = u.id AND status IN ('ACTIVE', 'UNACTIVATED') " +
            "    ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, id DESC " +
            "    LIMIT 1) " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN face_features ff ON ff.user_id = u.id " +
            "WHERE u.phone LIKE CONCAT('%', #{keyword}, '%') " +
            "ORDER BY u.created_at DESC " +
            "LIMIT #{offset}, #{size}")
    List<Map<String, Object>> findAll(@Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("size") int size);

    /**
     * 管理端：统计用户总数（带搜索）
     */
    @Select("SELECT COUNT(*) FROM users WHERE phone LIKE CONCAT('%', #{keyword}, '%')")
    long countAll(@Param("keyword") String keyword);

    /**
     * 管理端：统计今日新增会员数（按 users.created_at 当天）
     */
    @Select("SELECT COUNT(*) FROM users WHERE DATE(created_at) = CURDATE()")
    long countTodayNew();

    /**
     * 分人群筛选接收人（群发通知用）：
     * - cardTypeId 非空：持有该卡类型会员卡的用户（含任意状态）
     * - storeId 非空：持有该门店快照会员卡的用户
     * - 均非空时取交集；均为空等价于全部用户
     */
    @Select("<script>" +
            "SELECT DISTINCT u.id FROM users u " +
            "LEFT JOIN memberships m ON m.user_id = u.id " +
            "<if test='cardTypeId != null or storeId != null'>" +
            "WHERE 1 = 1 " +
            "<if test='cardTypeId != null'> AND m.card_type_id = #{cardTypeId} </if>" +
            "<if test='storeId != null'> AND m.store_id = #{storeId} </if>" +
            "</if>" +
            "</script>")
    List<Long> findIdsBySegment(@Param("cardTypeId") Long cardTypeId, @Param("storeId") Long storeId);

    /**
     * 分人群：持有指定卡类型的人数（群发预览用）
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM memberships WHERE card_type_id = #{cardTypeId}")
    long countByCardType(@Param("cardTypeId") Long cardTypeId);

    /**
     * 分人群：持有指定门店卡的人数（群发预览用）
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM memberships WHERE store_id = #{storeId}")
    long countByStore(@Param("storeId") Long storeId);

}
