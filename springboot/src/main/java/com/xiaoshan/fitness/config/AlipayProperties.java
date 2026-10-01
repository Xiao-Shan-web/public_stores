package com.xiaoshan.fitness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 沙箱 APPID */
    private String appId;

    /** 商家账号 PID */
    private String pid;

    /** 网关（沙箱） */
    private String gatewayUrl;

    /** 应用私钥（RSA2） */
    private String privateKey;

    /** 支付宝公钥 */
    private String alipayPublicKey;

    /** 异步回调地址（支付宝可访问的公网地址） */
    private String notifyUrl;

    /** 同步跳转地址（支付完成后前端跳转） */
    private String returnUrl;

    /** 签名类型 */
    private String signType = "RSA2";

    /** 字符集 */
    private String charset = "UTF-8";

    /** 格式 */
    private String format = "json";

}
