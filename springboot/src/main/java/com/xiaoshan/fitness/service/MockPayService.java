package com.xiaoshan.fitness.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 开发环境模拟支付
 * 直接返回支付成功，无需真实资金流转。
 * 当 app.pay.mode != alipay 时生效（默认）。
 */
@Service
@ConditionalOnProperty(name = "app.pay.mode", havingValue = "mock", matchIfMissing = true)
public class MockPayService implements PayService {

    @Override
    public PayResult pay(String orderNo, BigDecimal amount, String subject) {
        // 模拟支付：直接成功，交易号 = MOCK + 订单号
        return PayResult.okMock("MOCK_" + orderNo);
    }

}
