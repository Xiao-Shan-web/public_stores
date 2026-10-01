package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.Coupon;
import com.xiaoshan.fitness.entity.UserCoupon;
import com.xiaoshan.fitness.mapper.CouponMapper;
import com.xiaoshan.fitness.util.BusinessException;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发券执行器（独立 Bean，保证 @Transactional 生效）
 * <p>
 * 说明：原实现把发券逻辑放在 {@code CouponService} 的 protected 方法里由同类 this 调用，
 * Spring AOP 代理不生效导致事务失效（insert 失败时 issued_count 已 +1，出现「超发但没券」）。
 * 拆成独立 Bean 后由外部调用，事务边界正确：
 * <b>领取计数 +1 与发放记录要么同时成功，要么同时回滚</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssuer {

    private static final String VALID_FIXED = "FIXED";

    private final CouponMapper couponMapper;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 发券（DB 事务）：计数 +1 与发放记录必须同成功
     *
     * @param userId 用户ID
     * @param c      券模板（已通过前置校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> issue(Long userId, Coupon c) {
        // 条件更新：未超发才 +1（并发下靠 DB 行锁，绝不超发）
        int rows = couponMapper.incrIssuedCount(c.getId());
        if (rows == 0) {
            throw new BusinessException(400, "优惠券已被领完");
        }

        // 计算有效期
        LocalDateTime start;
        LocalDateTime end;
        if (VALID_FIXED.equalsIgnoreCase(c.getValidType())) {
            start = c.getStartTime() != null ? c.getStartTime() : LocalDateTime.now();
            end = c.getEndTime() != null ? c.getEndTime() : LocalDateTime.now().plusDays(30);
        } else {
            start = LocalDateTime.now();
            int days = c.getValidDays() == null ? 30 : c.getValidDays();
            end = start.plusDays(days);
        }
        if (end.isBefore(LocalDateTime.now())) {
            // 抛异常触发回滚，已 +1 的 issued_count 一并回滚
            throw new BusinessException(400, "该优惠券已过期");
        }

        UserCoupon uc = new UserCoupon();
        uc.setId(idGenerator.nextId());
        uc.setCouponId(c.getId());
        uc.setUserId(userId);
        uc.setStatus(CouponService.ST_UNUSED);
        uc.setStartTime(start);
        uc.setEndTime(end);
        couponMapper.insertUserCoupon(uc);

        log.info("用户{}领取优惠券[{}]{}，有效期至 {}", userId, c.getId(), c.getName(), end);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCouponId", uc.getId());
        data.put("couponId", c.getId());
        data.put("name", c.getName());
        data.put("type", c.getType());
        data.put("amount", c.getAmount());
        data.put("discount", c.getDiscount());
        data.put("maxDiscount", c.getMaxDiscount());
        data.put("endTime", end);
        return data;
    }
}
