package com.xiaoshan.fitness.controller;

import com.alipay.api.internal.util.AlipaySignature;
import com.xiaoshan.fitness.entity.CardOrder;
import com.xiaoshan.fitness.mapper.CardOrderMapper;
import com.xiaoshan.fitness.service.AlipayPayService;
import com.xiaoshan.fitness.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝回调控制器
 * <p>
 * 注意：所有回调处理都以"验签 + trade_status==TRADE_SUCCESS"为准，
 * 不依赖同步 return 回调更新支付状态。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pay/alipay")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.pay.mode", havingValue = "alipay")
public class AlipayController {

    private final AlipayPayService alipayPayService;
    private final CardOrderMapper cardOrderMapper;
    private final PaymentService paymentService;

    /**
     * 支付宝异步通知回调
     * POST /api/v1/pay/alipay/notify
     * <p>
     * 安全要点：
     *   1. SecurityConfig 已放行 /api/v1/pay/**，此接口不要求登录态
     *   2. 通过 AlipaySignature.rsaCheckV1 验签，确保请求来自支付宝
     *   3. 校验 trade_status == TRADE_SUCCESS / TRADE_FINISHED
     *   4. 校验订单金额
     *   5. 更新订单 + 生成会员卡 + 发 RabbitMQ 延迟过期消息
     *   6. 返回 plain text "success"，否则支付宝会重复通知
     */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        try {
            // 1. 收集支付宝回调参数
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> parameterMap = request.getParameterMap();
            for (String key : parameterMap.keySet()) {
                String[] values = parameterMap.get(key);
                if (values != null && values.length > 0) {
                    params.put(key, values[0]);
                }
            }

            log.info("收到支付宝异步通知：out_trade_no={}, trade_no={}, trade_status={}",
                    params.get("out_trade_no"), params.get("trade_no"), params.get("trade_status"));

            // 2. 验签
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayPayService.getAlipayPublicKey(),
                    alipayPayService.getCharset(),
                    alipayPayService.getSignType()
            );
            if (!signVerified) {
                log.warn("支付宝回调验签失败：out_trade_no={}", params.get("out_trade_no"));
                return "fail";
            }

            // 3. 检查交易状态（TRADE_SUCCESS/TRADE_FINISHED 才是最终支付成功）
            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                log.info("交易未完成：trade_status={}，out_trade_no={}", tradeStatus, params.get("out_trade_no"));
                return "success"; // 给支付宝响应成功，避免重复通知
            }

            // 4. 按 out_trade_no 查本地订单
            String orderNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            CardOrder order = cardOrderMapper.findByOrderNo(orderNo);
            if (order == null) {
                log.error("订单不存在：orderNo={}", orderNo);
                return "fail";
            }

            // 5. 校验金额（防止篡改）
            String totalAmountStr = params.get("total_amount");
            if (totalAmountStr != null) {
                try {
                    BigDecimal paidAmount = new BigDecimal(totalAmountStr);
                    if (order.getAmount().compareTo(paidAmount) != 0) {
                        log.error("订单金额不匹配：orderNo={}, 本地={}, 支付宝回调={}",
                                orderNo, order.getAmount(), totalAmountStr);
                        return "fail";
                    }
                } catch (NumberFormatException e) {
                    log.warn("金额格式异常：{}", totalAmountStr);
                }
            }

            // 6. 如果已是 PAID（重复通知），直接返回 success
            if ("PAID".equals(order.getStatus())) {
                log.info("订单{}已处理过，跳过", orderNo);
                return "success";
            }

            // 7. 调用 PaymentService.handlePaid → 生成会员卡(UNACTIVATED) + 更新订单（不发延迟消息，激活后才发）
            paymentService.handlePaid(order, tradeNo);

            log.info("支付宝回调处理完成：orderNo={}, tradeNo={}", orderNo, tradeNo);
            return "success";

        } catch (Exception e) {
            log.error("支付宝回调处理异常", e);
            return "fail";
        }
    }

}
