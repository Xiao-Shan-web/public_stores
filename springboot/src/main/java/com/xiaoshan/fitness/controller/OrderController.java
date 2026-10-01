package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.annotation.Idempotent;
import com.xiaoshan.fitness.entity.Activity;
import com.xiaoshan.fitness.entity.CardOrder;
import com.xiaoshan.fitness.entity.CardType;
import com.xiaoshan.fitness.mapper.ActivityMapper;
import com.xiaoshan.fitness.mapper.CardOrderMapper;
import com.xiaoshan.fitness.mapper.CardTypeMapper;
import com.xiaoshan.fitness.mapper.MembershipMapper;
import com.xiaoshan.fitness.service.CouponService;
import com.xiaoshan.fitness.service.PayResult;
import com.xiaoshan.fitness.service.PayService;
import com.xiaoshan.fitness.service.PaymentService;
import com.xiaoshan.fitness.util.BusinessException;
import com.xiaoshan.fitness.util.JwtUtil;
import com.xiaoshan.fitness.util.Result;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员卡购买订单接口（用户端）
 * <p>
 * 流程：选择卡类型 → 建单(PENDING) → 支付 → 生成会员卡(UNACTIVATED，未激活不发任何延迟消息)
 * 到期前 7 天/1 天提醒与到期延迟消息一律在「激活成功」后才调度。
 * <p>
 * 支付模式：
 *   - app.pay.mode=mock  (默认)：MockPayService 直接返回成功，OrderController.pay() 内调用 PaymentService.handlePaid()
 *   - app.pay.mode=alipay ：AlipayPayService 返回支付宝支付表单 HTML，前端渲染后跳支付宝；
 *                            支付结果由支付宝异步 notify 回调触发 PaymentService.handlePaid()
 */
@RestController
@RequestMapping("/api/v1/user/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final JwtUtil jwtUtil;
    private final CardTypeMapper cardTypeMapper;
    private final CardOrderMapper cardOrderMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final PayService payService;
    private final PaymentService paymentService;
    private final ActivityMapper activityMapper;
    private final CouponService couponService;
    private final MembershipMapper membershipMapper;

    /**
     * 创建订单：POST /api/v1/user/orders  body: { cardTypeId, userCouponId? }
     * <p>
     * 金额计算（服务端权威，前端传值一律忽略）：
     *   原价 = 卡类型价格
     *   活动价 = 原价 × 活动折扣（存在进行中且有名额的活动时）
     *   实付 = 活动价 - 券抵扣（券门槛按活动价校验）
     * <p>
     * 幂等窗口 10 秒：防止用户连点"购买"按钮导致生成多个 PENDING 订单。
     * <p>
     * 事务保证：建单、锁券、回写券抵扣三步入同一事务。任一步失败整体回滚，
     * 避免出现"券已 LOCKED 但订单没有券关联"的永久占用残留（状态联动的前提）。
     */
    @PostMapping
    @Idempotent(expireSeconds = 10)
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }

        Long cardTypeId = parseLong(body.get("cardTypeId"));
        if (cardTypeId == null) {
            return Result.fail(400, "请选择卡类型");
        }
        CardType ct = cardTypeMapper.findById(cardTypeId);
        if (ct == null || (ct.getIsDeleted() != null && ct.getIsDeleted() == 1)) {
            return Result.fail(404, "卡类型不存在");
        }
        if (ct.getIsActive() == null || ct.getIsActive() != 1) {
            return Result.fail(400, "该卡类型已下架");
        }

        // 1. 活动价：存在进行中且未超额的活动时按折扣计价
        BigDecimal originalPrice = ct.getPrice();
        BigDecimal payable = originalPrice;
        Long activityId = null;
        Activity ongoing = activityMapper.findOngoingByCardType(cardTypeId);
        if (ongoing != null && ongoing.getDiscount() != null) {
            payable = originalPrice.multiply(ongoing.getDiscount()).setScale(2, RoundingMode.HALF_UP);
            activityId = ongoing.getId();
        }

        Long userCouponId = parseLong(body.get("userCouponId"));

        CardOrder order = new CardOrder();
        order.setId(idGenerator.nextId());
        order.setOrderNo(idGenerator.generateOrderNumber());
        order.setUserId(userId);
        order.setCardType(ct.getName());
        order.setCardTypeId(ct.getId());
        order.setActivityId(activityId);
        order.setOriginalAmount(originalPrice);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setAmount(payable);
        cardOrderMapper.insert(order);

        // 2. 锁定券并抵扣（与建单同一事务：券校验/回写失败整体回滚，避免脏 PENDING 或悬挂 LOCKED 券）
        if (userCouponId != null) {
            BigDecimal cut = couponService.lockForOrder(userId, userCouponId, order.getId(), payable);
            BigDecimal finalAmount = payable.subtract(cut);
            if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
                finalAmount = BigDecimal.ZERO;
            }
            int rows = cardOrderMapper.updateCouponDiscount(order.getId(), userCouponId, cut, finalAmount);
            if (rows == 0) {
                // 订单已非 PENDING（异常并发），回滚锁券，避免券被占死却无订单承载
                throw new BusinessException(409, "订单状态已变更，优惠券锁定失败，请重试");
            }
            order.setUserCouponId(userCouponId);
            order.setDiscountAmount(cut);
            order.setAmount(finalAmount);
        }

        log.info("用户{}创建订单：{}（{}，原价{}，活动{}，券抵扣{}，实付{}元）",
                userId, order.getOrderNo(), ct.getName(), originalPrice,
                activityId, order.getDiscountAmount(), order.getAmount());

        Map<String, Object> data = buildOrderVO(order);
        data.put("cardTypeName", ct.getName());
        data.put("originalAmount", originalPrice.toPlainString());
        return Result.ok(data, "下单成功");
    }

    /**
     * 发起支付：POST /api/v1/user/orders/{orderNo}/pay
     * <p>
     * mock 模式：直接支付成功 → 生成会员卡（UNACTIVATED），不发延迟消息（激活后才发）
     * alipay 模式：返回支付宝支付表单 HTML，由前端渲染后跳支付宝收银台
     * <p>
     * 幂等窗口 10 秒：幂等 key 维度为 userId + URI（含 orderNo），
     * 即同一用户对同一订单在 10 秒内只能支付一次，防止连点导致重复进入支付流程。
     * 业务幂等（订单状态非 PENDING）由 DB 状态校验兜底。
     */
    @PostMapping("/{orderNo}/pay")
    @Idempotent(expireSeconds = 10)
    public Result<Map<String, Object>> pay(@PathVariable String orderNo,
                                           HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }

        CardOrder order = cardOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail(400, "无权操作该订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            return Result.fail(400, "订单状态不允许支付");
        }

        // 调用支付适配器
        PayResult payResult = payService.pay(order.getOrderNo(), order.getAmount(), order.getCardType());
        if (!payResult.isSuccess()) {
            return Result.fail(400, "支付失败：" + payResult.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", order.getOrderNo());

        if (payResult.getPayFormHtml() != null || payResult.getPayUrl() != null) {
            // Alipay 模式：优先返回 payUrl（前端 window.location.href 直达收银台），
            //              兼容返回 payFormHtml（前端隐藏容器 form.submit 兜底）。
            // 券核销在异步回调 handlePaid 后统一处理。
            if (payResult.getPayUrl() != null) {
                data.put("payUrl", payResult.getPayUrl());
            }
            if (payResult.getPayFormHtml() != null) {
                data.put("payFormHtml", payResult.getPayFormHtml());
            }
            data.put("payMode", "alipay");
            return Result.ok(data, "请在支付宝完成支付");
        }

        // Mock 模式：直接走 handlePaid（券核销在 PaymentService 内统一处理）
        Long membershipId = paymentService.handlePaid(order, payResult.getTradeNo());
        data.put("status", "PAID");
        data.put("membershipId", membershipId);
        data.put("payMode", "mock");
        return Result.ok(data, "支付成功，会员卡已生成（待到店激活）");
    }

    /**
     * 取消订单：POST /api/v1/user/orders/{orderNo}/cancel
     * <p>
     * 仅 PENDING 可取消；已锁定的券同步释放（未过期归还用户，已过期置 EXPIRED）。
     */
    @PostMapping("/{orderNo}/cancel")
    public Result<String> cancel(@PathVariable String orderNo, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        CardOrder order = cardOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail(400, "无权操作该订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            return Result.fail(400, "仅待支付订单可取消");
        }

        int rows = cardOrderMapper.cancelPending(order.getId());
        if (rows == 0) {
            return Result.fail(409, "订单状态已变更，请刷新后重试");
        }
        // 释放券
        couponService.release(order.getUserCouponId(), order.getId());
        log.info("用户{}取消订单{}，券{}已释放", userId, orderNo, order.getUserCouponId());
        return Result.ok("订单已取消");
    }

    /**
     * 订单退款：POST /api/v1/user/orders/{orderNo}/refund
     * <p>
     * 仅已支付(PAID)订单可退款。退款为业务级状态流转（真实资金需对接支付渠道退款接口）：
     * <ol>
     *   <li>订单 PAID → REFUNDED（乐观锁防重复退款）</li>
     *   <li>购卡生成的会员卡置为 DISABLED（退款后不可继续使用）</li>
     *   <li>优惠券联动：未过期 → 退还 UNUSED；已过期 → 作废 EXPIRED</li>
     * </ol>
     */
    @PostMapping("/{orderNo}/refund")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> refund(@PathVariable String orderNo, HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        CardOrder order = cardOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail(400, "无权操作该订单");
        }
        if (!"PAID".equals(order.getStatus())) {
            return Result.fail(400, "仅已支付订单可退款");
        }

        int rows = cardOrderMapper.markRefunded(order.getId());
        if (rows == 0) {
            return Result.fail(409, "订单状态已变更，请刷新后重试");
        }
        // 退款后停用已生成的会员卡
        if (order.getMembershipId() != null) {
            membershipMapper.disable(order.getMembershipId());
        }
        // 券按业务规则联动：未过期退回，已过期作废
        couponService.refund(order.getUserCouponId(), order.getId());
        log.info("用户{}订单{}已退款，券{}按规则处理（会员卡{}停用）",
                userId, orderNo, order.getUserCouponId(), order.getMembershipId());
        return Result.ok("退款成功");
    }

    /**
     * 订单详情：GET /api/v1/user/orders/{orderNo}
     */
    @GetMapping("/{orderNo}")
    public Result<Map<String, Object>> detail(@PathVariable String orderNo,
                                               HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        CardOrder order = cardOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权查看该订单");
        }
        return Result.ok(buildOrderVO(order));
    }

    /**
     * 我的订单列表：GET /api/v1/user/orders?page=1&size=20
     */
    @GetMapping
    public Result<Map<String, Object>> list(HttpServletRequest request,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.fail("未登录或登录已过期");
        }
        int offset = Math.max(0, (page - 1) * size);
        List<CardOrder> orders = cardOrderMapper.findPageByUserId(userId, offset, size);
        long total = cardOrderMapper.countByUserId(userId);

        List<Map<String, Object>> list = orders.stream()
                .map(this::buildOrderVO)
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    // ==================== 私有辅助 ====================

    private Map<String, Object> buildOrderVO(CardOrder o) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", o.getId());
        item.put("orderNo", o.getOrderNo());
        item.put("tradeNo", o.getTradeNo());
        item.put("cardType", o.getCardType());
        item.put("cardTypeId", o.getCardTypeId());
        item.put("amount", o.getAmount() != null ? o.getAmount().toPlainString() : null);
        item.put("originalAmount", o.getOriginalAmount() != null ? o.getOriginalAmount().toPlainString() : null);
        item.put("activityId", o.getActivityId());
        item.put("userCouponId", o.getUserCouponId());
        item.put("discountAmount", o.getDiscountAmount() != null ? o.getDiscountAmount().toPlainString() : "0.00");
        item.put("status", o.getStatus());
        item.put("payTime", o.getPayTime());
        item.put("paidAt", o.getPaidAt());
        item.put("membershipId", o.getMembershipId());
        item.put("createdAt", o.getCreatedAt());
        return item;
    }

    private Long currentUserId(HttpServletRequest request) {
        String token = jwtUtil.extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        if (!JwtUtil.TYPE_USER.equals(jwtUtil.getTypeFromToken(token))) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Number) return ((Number) o).longValue();
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
