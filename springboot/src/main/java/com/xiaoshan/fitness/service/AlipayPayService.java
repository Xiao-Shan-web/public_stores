package com.xiaoshan.fitness.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.xiaoshan.fitness.config.AlipayProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLEncoder;

/**
 * 支付宝沙箱支付实现
 * <p>
 * 使用 alipay-sdk-java（4.38.157.ALL）发起页面支付。
 * 仅当 app.pay.mode=alipay 时激活；否则 MockPayService 生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.pay.mode", havingValue = "alipay")
public class AlipayPayService implements PayService {

    private final AlipayProperties alipayProperties;

    private AlipayClient alipayClient;

    @PostConstruct
    public void init() {
        this.alipayClient = new DefaultAlipayClient(
                alipayProperties.getGatewayUrl(),
                alipayProperties.getAppId(),
                alipayProperties.getPrivateKey(),
                alipayProperties.getFormat(),
                alipayProperties.getCharset(),
                alipayProperties.getAlipayPublicKey(),
                alipayProperties.getSignType()
        );
        log.info("AlipayPayService 已初始化，appId={}", alipayProperties.getAppId());
    }

    @Override
    public PayResult pay(String orderNo, BigDecimal amount, String subject) {
        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(alipayProperties.getNotifyUrl());
            request.setReturnUrl(alipayProperties.getReturnUrl());

            // 业务参数
            request.setBizContent("{" +
                    "\"out_trade_no\":\"" + orderNo + "\"," +
                    "\"total_amount\":\"" + amount.toPlainString() + "\"," +
                    "\"subject\":\"" + subject + "\"," +
                    "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"" +
                    "}");

            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);

            if (response.isSuccess()) {
                PayResult result = new PayResult();
                result.setSuccess(true);
                // 兼容字段：表单 HTML（兜底路径，前端优先走 payUrl）
                result.setPayFormHtml(response.getBody());
                // 优先路径：由 SDK 已签名表单参数拼出 GET 跳转地址，
                // 前端 window.location.href 直达收银台，彻底规避新窗口/隐藏容器提交被拦截。
                result.setPayUrl(buildGetPayUrl(response.getBody()));
                result.setMessage("请在支付宝完成支付");
                return result;
            } else {
                log.error("支付宝下单失败：subCode={}，msg={}", response.getSubCode(), response.getSubMsg());
                return PayResult.fail("支付宝下单失败：" + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            log.error("支付宝支付请求异常", e);
            return PayResult.fail("支付宝支付异常：" + e.getMessage());
        }
    }

    /**
     * 解析支付宝 SDK 返回的自动提交表单 HTML，拼出可直接 location.href 的 GET 跳转地址。
     * <p>
     * SDK 已完成 RSA2 签名，表单内含全部已签名参数（method/app_id/biz_content/sign/
     * sign_type/timestamp/notify_url/return_url/charset/format 等），支付宝统一网关同时
     * 支持 GET/POST，按表单 action + 参数 querystring 拼接即可直接跳转，签名校验不受传输方式影响。
     * <p>
     * 相比"隐藏容器注入 form + 调 form.submit()"，location.href 是顶层导航，浏览器永不拦截，
     * 本机与服务器行为完全一致。
     */
    private String buildGetPayUrl(String formHtml) {
        if (formHtml == null || formHtml.isEmpty()) {
            return null;
        }
        // 1. 提取表单 action（网关地址）
        Matcher actionMatcher = ACTION_PATTERN.matcher(formHtml);
        if (!actionMatcher.find()) {
            log.warn("解析支付宝表单 action 失败，回退表单 HTML 路径");
            return null;
        }
        String action = actionMatcher.group(1);

        // 2. 提取所有 hidden input 的 name/value（属性顺序与引号类型无关）
        List<String[]> pairs = new ArrayList<>();
        Matcher inputMatcher = INPUT_TAG_PATTERN.matcher(formHtml);
        while (inputMatcher.find()) {
            String tag = inputMatcher.group();
            String name = extractAttr(tag, "name");
            String value = extractAttr(tag, "value");
            // 仅收集带 name 的隐藏字段（跳过 type=submit 等）
            if (name != null && !name.isEmpty()) {
                pairs.add(new String[]{name, value == null ? "" : value});
            }
        }
        if (pairs.isEmpty()) {
            log.warn("解析支付宝表单参数为空，回退表单 HTML 路径");
            return null;
        }

        // 3. 拼接 GET URL：参数值统一 URLEncoder 编码（+ 表示空格，网关标准解码）
        StringBuilder sb = new StringBuilder(action);
        sb.append(action.contains("?") ? '&' : '?');
        boolean first = true;
        for (String[] p : pairs) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(URLEncoder.encode(p[0], StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(p[1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** 提取 input 标签内某属性的值（自动适配单/双引号，并做最小 HTML 实体反转义） */
    private static String extractAttr(String tag, String attr) {
        Matcher m = Pattern.compile("\\b" + attr + "=(['\"])(.*?)\\1").matcher(tag);
        if (!m.find()) {
            return null;
        }
        return unescapeHtml(m.group(2));
    }

    private static String unescapeHtml(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static final Pattern ACTION_PATTERN = Pattern.compile("action=['\"]([^'\"]+)['\"]");
    private static final Pattern INPUT_TAG_PATTERN = Pattern.compile("<input\\b[^>]*>");

    /** 获取 AlipayClient（供 AlipayController 验签使用） */
    public AlipayClient getAlipayClient() {
        return alipayClient;
    }

    /** 获取支付宝公钥（供 AlipayController 验签使用） */
    public String getAlipayPublicKey() {
        return alipayProperties.getAlipayPublicKey();
    }

    public String getCharset() {
        return alipayProperties.getCharset();
    }

    public String getSignType() {
        return alipayProperties.getSignType();
    }

}
