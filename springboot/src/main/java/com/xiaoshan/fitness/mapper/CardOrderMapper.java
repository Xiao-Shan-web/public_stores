package com.xiaoshan.fitness.mapper;

import com.xiaoshan.fitness.entity.CardOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 购买订单 Mapper
 */
@Mapper
public interface CardOrderMapper {

    /** 新建订单（状态 PENDING；记录活动ID/券ID/原价/券抵扣） */
    @Insert("INSERT INTO card_orders(id, order_no, user_id, card_type, card_type_id, activity_id, user_coupon_id, " +
            "discount_amount, original_amount, amount, status) " +
            "VALUES(#{id}, #{orderNo}, #{userId}, #{cardType}, #{cardTypeId}, #{activityId}, #{userCouponId}, " +
            "#{discountAmount}, #{originalAmount}, #{amount}, 'PENDING')")
    int insert(CardOrder order);

    /**
     * 下单锁定券后回写券信息与实付金额（券抵扣在订单创建后单独事务完成）
     */
    @Update("UPDATE card_orders SET user_coupon_id = #{userCouponId}, " +
            "discount_amount = #{discountAmount}, amount = #{amount} " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int updateCouponDiscount(@Param("id") Long id,
                             @Param("userCouponId") Long userCouponId,
                             @Param("discountAmount") java.math.BigDecimal discountAmount,
                             @Param("amount") java.math.BigDecimal amount);

    /**
     * 取消订单（仅 PENDING 可取消）
     */
    @Update("UPDATE card_orders SET status = 'CANCELLED' WHERE id = #{id} AND status = 'PENDING'")
    int cancelPending(@Param("id") Long id);

    /**
     * 查出超时未支付的订单（不论是否占用优惠券），供 OrderTimeoutScanTask 统一取消。
     * <p>
     * 是否占券都取消：订单状态本身也要闭环（此前只扫带券订单，导致无券的超时单永远停在 PENDING）。
     * user_coupon_id 为空时调用方跳过释放即可。
     */
    @Select("SELECT id, user_coupon_id AS userCouponId FROM card_orders " +
            "WHERE status = 'PENDING' " +
            "AND created_at < DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)")
    List<Map<String, Object>> findTimeoutPending(@Param("minutes") int minutes);

    /**
     * 退款：PAID → REFUNDED（乐观锁，仅已支付订单可退，防重复退款）
     */
    @Update("UPDATE card_orders SET status = 'REFUNDED' WHERE id = #{id} AND status = 'PAID'")
    int markRefunded(@Param("id") Long id);

    /**
     * 关闭单个超时未支付订单（PENDING → CANCELLED），带状态前置条件防并发
     */
    @Update("UPDATE card_orders SET status = 'CANCELLED' WHERE id = #{id} AND status = 'PENDING'")
    int cancelTimeoutOrder(@Param("id") Long id);

    /** 按订单号查询 */
    @Select("SELECT id, order_no AS orderNo, trade_no AS tradeNo, user_id AS userId, membership_id AS membershipId, " +
            "card_type AS cardType, card_type_id AS cardTypeId, activity_id AS activityId, user_coupon_id AS userCouponId, discount_amount AS discountAmount, original_amount AS originalAmount, amount, status, " +
            "pay_time AS payTime, paid_at AS paidAt, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM card_orders WHERE order_no = #{orderNo}")
    CardOrder findByOrderNo(@Param("orderNo") String orderNo);

    /** 按第三方交易号查询（支付宝回调用） */
    @Select("SELECT id, order_no AS orderNo, trade_no AS tradeNo, user_id AS userId, membership_id AS membershipId, " +
            "card_type AS cardType, card_type_id AS cardTypeId, activity_id AS activityId, user_coupon_id AS userCouponId, discount_amount AS discountAmount, original_amount AS originalAmount, amount, status, " +
            "pay_time AS payTime, paid_at AS paidAt, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM card_orders WHERE trade_no = #{tradeNo}")
    CardOrder findByTradeNo(@Param("tradeNo") String tradeNo);

    /** 按ID查询 */
    @Select("SELECT id, order_no AS orderNo, trade_no AS tradeNo, user_id AS userId, membership_id AS membershipId, " +
            "card_type AS cardType, card_type_id AS cardTypeId, activity_id AS activityId, user_coupon_id AS userCouponId, discount_amount AS discountAmount, original_amount AS originalAmount, amount, status, " +
            "pay_time AS payTime, paid_at AS paidAt, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM card_orders WHERE id = #{id}")
    CardOrder findById(@Param("id") Long id);

    /** 用户订单分页列表（按创建时间倒序） */
    @Select("SELECT id, order_no AS orderNo, trade_no AS tradeNo, user_id AS userId, membership_id AS membershipId, " +
            "card_type AS cardType, card_type_id AS cardTypeId, activity_id AS activityId, user_coupon_id AS userCouponId, discount_amount AS discountAmount, original_amount AS originalAmount, amount, status, " +
            "pay_time AS payTime, paid_at AS paidAt, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM card_orders WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<CardOrder> findPageByUserId(@Param("userId") Long userId,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    /** 用户订单总数 */
    @Select("SELECT COUNT(*) FROM card_orders WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 标记订单已支付（支付宝回调时调用）
     * 同时写入 trade_no + paid_at
     * 仅当当前状态为 PENDING 时更新（乐观锁，防重复支付）
     */
    @Update("UPDATE card_orders SET status = #{status}, pay_time = #{payTime}, paid_at = #{paidAt}, " +
            "membership_id = #{membershipId}, trade_no = #{tradeNo} " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int updatePaid(@Param("id") Long id,
                   @Param("status") String status,
                   @Param("payTime") LocalDateTime payTime,
                   @Param("paidAt") LocalDateTime paidAt,
                   @Param("membershipId") Long membershipId,
                   @Param("tradeNo") String tradeNo);

    /**
     * 简化版：仅更新订单状态 + pay_time（MockPayService 用，不关联 membership）
     */
    @Update("UPDATE card_orders SET status = #{status}, pay_time = #{payTime} " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int updatePaidSimple(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("payTime") LocalDateTime payTime);

}
