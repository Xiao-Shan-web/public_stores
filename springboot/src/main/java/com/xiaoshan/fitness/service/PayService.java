package com.xiaoshan.fitness.service;

import java.math.BigDecimal;

/**
 * 支付适配器
 * <p>
 * 当前 MockPayService 直接模拟支付成功（开发环境）。
 * 接入真实支付宝时，新建 AlipayPayService 实现本接口：
 *   - pay() 返回支付宝跳转URL，前端重定向到支付宝收银台；
 *   - 支付结果由支付宝异步 notify 回调写入，回调中调用 handlePaid 流程。
 */
public interface PayService {

    /**
     * 发起支付
     *
     * @param orderNo 订单号
     * @param amount  金额
     * @param subject 订单标题（如"月卡-30天"）
     * @return 支付结果（mock 模式 success=true；真实 Alipay 返回跳转URL）
     */
    PayResult pay(String orderNo, BigDecimal amount, String subject);

}
