package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.Membership;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会员卡 Mapper
 * 状态机：UNACTIVATED（新购未激活）→ ACTIVE（人脸录入后激活）→ EXPIRED（到期）/ DISABLED（管理员停用）
 */
@Mapper
public interface MembershipMapper {

    /**
     * 查询用户最新的会员卡（任意状态，按创建时间倒序）
     */
    @Select("SELECT id, user_id AS userId, card_no AS cardNo, card_type AS cardType, " +
            "card_type_id AS cardTypeId, store_id AS storeId, " +
            "total_times AS totalTimes, remaining_times AS remainingTimes, " +
            "start_time AS startTime, end_time AS endTime, status, " +
            "created_at AS createdAt, updated_at AS updatedAt " +
            "FROM memberships WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT 1")
    Optional<Membership> findLatestByUserId(@Param("userId") Long userId);

    /**
     * 查询用户最新会员卡（LEFT JOIN card_types/stores 取卡类型名称、分类、范围、门店，用于用户端展示）
     */
    @Select("SELECT m.id, m.user_id AS userId, m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE m.user_id = #{userId} ORDER BY m.created_at DESC LIMIT 1")
    Optional<Membership> findLatestByUserIdWithCardType(@Param("userId") Long userId);

    /**
     * 核销用：优先查用户的 ACTIVE 卡（按 created_at DESC，取第一张有效卡）。
     * 若无 ACTIVE 卡则返回最新卡（用于给出"未激活/已过期"等明确失败原因）。
     */
    @Select("SELECT m.id, m.user_id AS userId, m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE m.user_id = #{userId} AND m.status = 'ACTIVE' " +
            "ORDER BY m.created_at DESC LIMIT 1")
    Optional<Membership> findActiveByUserId(@Param("userId") Long userId);

    /**
     * 核销用：查询用户可入场的 ACTIVE 普通卡（NORMAL）。
     * 排序：全店通用（ALL_STORE）优先，其次单店卡；同范围按办理时间倒序，取第一张。
     * 历史无 card_type_id 的卡按普通卡处理。私教课卡（PT）不参与入场核销。
     */
    @Select("SELECT m.id, m.user_id AS userId, m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE m.user_id = #{userId} AND m.status = 'ACTIVE' " +
            "AND (ct.category = 'NORMAL' OR m.card_type_id IS NULL) " +
            "ORDER BY CASE WHEN m.store_id IS NULL THEN 0 ELSE 1 END, " +
            "m.created_at DESC LIMIT 1")
    Optional<Membership> findActiveNormalByUserId(@Param("userId") Long userId);

    /**
     * 核销失败兜底：查询用户最新一张普通卡（NORMAL，任意状态，用于给出"未激活/已过期"等明确原因）。
     */
    @Select("SELECT m.id, m.user_id AS userId, m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE m.user_id = #{userId} " +
            "AND (ct.category = 'NORMAL' OR m.card_type_id IS NULL) " +
            "ORDER BY m.created_at DESC LIMIT 1")
    Optional<Membership> findLatestNormalByUserId(@Param("userId") Long userId);

    /**
     * 激活互斥校验用：当前读（FOR UPDATE）锁定并返回该用户全部 ACTIVE 会员卡（含分类、范围、门店）。
     * <p>
     * 必须在激活事务内直接使用其返回结果做冲突判定，不能再用普通 SELECT：
     * InnoDB 可重复读下一致性读会沿用事务早期的读视图，可能漏看并发事务刚提交的 ACTIVE 卡；
     * 而 FOR UPDATE 为锁定读，始终读最新已提交版本——并发激活互斥卡时，
     * 后到事务会在此等待先提交事务释放行锁，随后读到新生效卡并拒绝激活。
     */
    @Select("SELECT m.id, m.user_id AS userId, m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE m.user_id = #{userId} AND m.status = 'ACTIVE' " +
            "FOR UPDATE")
    List<Membership> findActiveByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 查询用户全部会员卡（含卡类型名称/分类/范围/门店，按创建时间倒序）
     */
    @Select("SELECT m.id, m.user_id AS userId, m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE m.user_id = #{userId} ORDER BY m.created_at DESC")
    List<Membership> findAllByUserId(@Param("userId") Long userId);

    /**
     * 根据会员卡ID查询（含卡类型名称/分类/范围/门店，用于核销与激活校验）
     */
    @Select("SELECT m.id, m.user_id AS userId, m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE m.id = #{id}")
    Optional<Membership> findById(@Param("id") Long id);

    /**
     * 插入会员卡（购买支付成功后生成，状态 UNACTIVATED；storeId 为购卡门店快照）
     */
    @Insert("INSERT INTO memberships(id, user_id, card_no, card_type, card_type_id, store_id, " +
            "total_times, remaining_times, start_time, end_time, status) " +
            "VALUES(#{id}, #{userId}, #{cardNo}, #{cardType}, #{cardTypeId}, #{storeId}, " +
            "#{totalTimes}, #{remainingTimes}, #{startTime}, #{endTime}, #{status})")
    int insert(Membership membership);

    /**
     * 激活会员卡（人脸录入成功后调用）
     * 状态从 UNACTIVATED 改为 ACTIVE，写入 start_time=now、end_time=now+duration_days
     * 仅当当前状态为 UNACTIVATED 时才更新（避免重复激活）
     */
    @Update("UPDATE memberships SET status = 'ACTIVE', " +
            "start_time = #{startTime}, end_time = #{endTime} " +
            "WHERE id = #{id} AND status = 'UNACTIVATED'")
    int activate(@Param("id") Long id,
                @Param("startTime") java.time.LocalDateTime startTime,
                @Param("endTime") java.time.LocalDateTime endTime);

    /**
     * 停用会员卡（管理员手动停用异常卡）
     * 状态改为 DISABLED
     */
    @Update("UPDATE memberships SET status = 'DISABLED' WHERE id = #{id}")
    int disable(@Param("id") Long id);

    /**
     * 标记会员卡过期（RabbitMQ 延迟消息消费时调用）
     * 仅允许 ACTIVE 状态改为 EXPIRED；UNACTIVATED 卡未激活不参与自动过期，DISABLED 不允许覆盖
     */
    @Update("UPDATE memberships SET status = 'EXPIRED' WHERE id = #{id} AND status = 'ACTIVE'")
    int markExpired(@Param("id") Long id);

    /**
     * 定时扫描兜底：查已到期的 ACTIVE 卡ID（end_time <= now）
     * 供 MembershipExpireScanTask 补偿丢失/被截断的到期延迟消息
     */
    @Select("SELECT id FROM memberships WHERE status = 'ACTIVE' " +
            "AND end_time IS NOT NULL AND end_time <= #{now}")
    List<Long> findActiveExpiredIds(@Param("now") java.time.LocalDateTime now);

    /**
     * 定时扫描兜底：查 N 天内将到期的 ACTIVE 卡ID（now < end_time <= deadline）
     * 供 MembershipExpireScanTask 补偿丢失/被截断的到期前提醒消息
     */
    @Select("SELECT id FROM memberships WHERE status = 'ACTIVE' " +
            "AND end_time IS NOT NULL AND end_time > #{now} AND end_time <= #{deadline}")
    List<Long> findActiveIdsExpiringBetween(@Param("now") java.time.LocalDateTime now,
                                            @Param("deadline") java.time.LocalDateTime deadline);

    /**
     * 次卡核销扣减一次剩余次数（remaining_times - 1）
     * 仅当剩余次数 > 0 时更新
     */
    @Update("UPDATE memberships SET remaining_times = remaining_times - 1 " +
            "WHERE id = #{id} AND remaining_times > 0")
    int deductRemainingTimes(@Param("id") Long id);

    /**
     * 管理端：分页查询全部会员卡记录（关联用户手机号 + 卡类型名称/分类/范围/门店），按创建时间倒序
     * 支持筛选：status 状态、storeId 门店、cardTypeId 卡种、phone 手机号模糊、cardNo 卡号尾号模糊
     * phone 与 cardNo 为 OR 关系：任一命中即返回；两者同时给定时用括号包裹 OR 表达式
     */
    @Select("<script>" +
            "SELECT m.id AS id, m.user_id AS userId, u.phone AS phone, " +
            "m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status AS status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN users u ON u.id = m.user_id " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND m.status = #{status}</if>" +
            "  <if test='storeId != null'> AND m.store_id = #{storeId}</if>" +
            "  <if test='cardTypeId != null'> AND m.card_type_id = #{cardTypeId}</if>" +
            "  <if test='(phone != null and phone != \"\") or (cardNo != null and cardNo != \"\")'>" +
            "    AND (<trim prefixOverrides='OR'>" +
            "      <if test='phone != null and phone != \"\"'> OR u.phone LIKE CONCAT('%', #{phone}, '%')</if>" +
            "      <if test='cardNo != null and cardNo != \"\"'> OR m.card_no LIKE CONCAT('%', #{cardNo})</if>" +
            "    </trim>)" +
            "  </if>" +
            "</where>" +
            "ORDER BY m.created_at DESC " +
            "LIMIT #{offset}, #{size}</script>")
    List<Map<String, Object>> findAll(@Param("status") String status,
                                       @Param("storeId") Long storeId,
                                       @Param("cardTypeId") Long cardTypeId,
                                       @Param("phone") String phone,
                                       @Param("cardNo") String cardNo,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /**
     * 管理端：统计会员卡总数（支持筛选，与 findAll 同口径）
     * phone 与 cardNo 为 OR 关系
     */
    @Select("<script>SELECT COUNT(*) FROM memberships m " +
            "LEFT JOIN users u ON u.id = m.user_id " +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND m.status = #{status}</if>" +
            "  <if test='storeId != null'> AND m.store_id = #{storeId}</if>" +
            "  <if test='cardTypeId != null'> AND m.card_type_id = #{cardTypeId}</if>" +
            "  <if test='(phone != null and phone != \"\") or (cardNo != null and cardNo != \"\")'>" +
            "    AND (<trim prefixOverrides='OR'>" +
            "      <if test='phone != null and phone != \"\"'> OR u.phone LIKE CONCAT('%', #{phone}, '%')</if>" +
            "      <if test='cardNo != null and cardNo != \"\"'> OR m.card_no LIKE CONCAT('%', #{cardNo})</if>" +
            "    </trim>)" +
            "  </if>" +
            "</where></script>")
    long countAll(@Param("status") String status,
                  @Param("storeId") Long storeId,
                  @Param("cardTypeId") Long cardTypeId,
                  @Param("phone") String phone,
                  @Param("cardNo") String cardNo);

    /**
     * 管理端：会员卡详情（含用户手机号 + 卡类型/分类/范围/门店 + 完整时间），用于详情页
     */
    @Select("SELECT m.id AS id, m.user_id AS userId, u.phone AS phone, " +
            "m.card_no AS cardNo, m.card_type AS cardType, " +
            "m.card_type_id AS cardTypeId, m.store_id AS storeId, ct.name AS cardTypeName, " +
            "ct.category AS category, " +
            "CASE WHEN m.store_id IS NULL THEN 'ALL_STORE' ELSE 'SINGLE_STORE' END AS scope, " +
            "s.name AS storeName, " +
            "m.total_times AS totalTimes, m.remaining_times AS remainingTimes, " +
            "m.start_time AS startTime, m.end_time AS endTime, m.status AS status, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM memberships m " +
            "LEFT JOIN users u ON u.id = m.user_id " +
            "LEFT JOIN card_types ct ON ct.id = m.card_type_id " +
            "LEFT JOIN stores s ON s.id = m.store_id " +
            "WHERE m.id = #{id}")
    Map<String, Object> findDetailById(@Param("id") Long id);

    /**
     * 管理端：手动延期（延长到期时间；EXPIRED 卡延期后自动恢复为 ACTIVE）
     * 仅允许 ACTIVE/EXPIRED 状态延期；UNACTIVATED/DISABLED 不允许
     */
    @Update("UPDATE memberships SET end_time = #{newEndTime}, " +
            "status = CASE WHEN status = 'EXPIRED' THEN 'ACTIVE' ELSE status END " +
            "WHERE id = #{id} AND status IN ('ACTIVE','EXPIRED')")
    int extendEndTime(@Param("id") Long id,
                      @Param("newEndTime") java.time.LocalDateTime newEndTime);

    /**
     * 管理端：手动调整剩余次数（仅次卡，remaining_times 非空）
     * 设置为指定新值，不允许负数
     */
    @Update("UPDATE memberships SET remaining_times = #{newValue} " +
            "WHERE id = #{id} AND remaining_times IS NOT NULL AND #{newValue} >= 0")
    int setRemainingTimes(@Param("id") Long id, @Param("newValue") int newValue);

    /**
     * 管理端：查询某张会员卡的操作记录（来自管理员审计日志，按 URI 前缀匹配该卡的全部写操作）
     * 覆盖 activate/disable/extend/times 等动作，param_summary 含原因/参数
     */
    @Select("SELECT id, admin_id AS adminId, username, module, action, method, " +
            "uri, param_summary AS paramSummary, ip, result, cost_ms AS costMs, created_at AS createdAt " +
            "FROM admin_audit_logs " +
            "WHERE uri LIKE CONCAT('/api/v1/admin/memberships/', #{id}, '/%') " +
            "ORDER BY created_at DESC LIMIT 50")
    List<Map<String, Object>> findLogsByMembershipId(@Param("id") Long id);

    /**
     * 管理端：统计有效会员卡数（status=ACTIVE）
     */
    @Select("SELECT COUNT(*) FROM memberships WHERE status = 'ACTIVE'")
    long countActive();

    /**
     * 管理端：按卡类型统计分布
     */
    @Select("SELECT card_type AS type, COUNT(*) AS count " +
            "FROM memberships GROUP BY card_type")
    List<Map<String, Object>> distributionByType();

}
