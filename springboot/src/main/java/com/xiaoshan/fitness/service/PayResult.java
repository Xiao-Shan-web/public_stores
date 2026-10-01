package com.xiaoshan.fitness.service;

import lombok.Data;

/**
 * 支付结果
 */
@Data
public class PayResult {

    /** 是否支付成功 */
    private boolean success;

    /** 支付跳转 URL（部分支付场景返回） */
    private String redirectUrl;

    /** 支付宝支付表单 HTML（AlipayTradePagePay 返回的 body，前端渲染后自动提交到支付宝） */
    private String payFormHtml;

    /**
     * 支付宝 GET 跳转地址（由 SDK 已签名表单参数拼接而成，前端直接 window.location.href 跳转）。
     * 优先使用本字段，规避 window.open + document.write / 隐藏容器 form.submit()
     * 在异步请求后被部分浏览器拦截导致服务器空白页的问题。
     */
    private String payUrl;

    /** 第三方交易号 */
    private String tradeNo;

    /** 提示信息 */
    private String message;

    public static PayResult okMock(String tradeNo) {
        PayResult r = new PayResult();
        r.success = true;
        r.tradeNo = tradeNo;
        r.message = "模拟支付成功";
        return r;
    }

    public static PayResult fail(String message) {
        PayResult r = new PayResult();
        r.success = false;
        r.message = message;
        return r;
    }

}
