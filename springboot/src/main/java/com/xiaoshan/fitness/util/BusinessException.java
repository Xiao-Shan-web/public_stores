package com.xiaoshan.fitness.util;

/**
 * 业务异常
 * <p>
 * 用于业务逻辑错误（如"手机号已存在"、"会员卡已过期"），
 * message 为可直接展示给前端的友好提示，不包含 SQL/堆栈/类名等内部信息。
 * 由 GlobalExceptionHandler 捕获后原样返回 message + code。
 * <p>
 * code 为业务码（与 Result 对齐），便于前端按错误类型差异化处理：
 * <ul>
 *   <li>400：默认，普通业务错误</li>
 *   <li>401：未登录/登录已过期</li>
 *   <li>403：无权限</li>
 *   <li>404：资源不存在</li>
 *   <li>409：状态冲突（重复支付、已激活等）</li>
 *   <li>429：限流/幂等拦截</li>
 * </ul>
 */
public class BusinessException extends RuntimeException {

    /** 业务码，默认 400 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 400;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
