package com.xiaoshan.fitness.util;

import lombok.Data;

/**
 * 统一响应封装
 * <p>
 * 结构：{ success, message, data, code }，与前端 ApiResponse<T> 对齐。
 * code 为业务码（不是 HTTP status），用于前端按错误类型做差异化处理：
 * <ul>
 *   <li>200：成功</li>
 *   <li>400：业务/参数错误（默认业务异常码）</li>
 *   <li>401：未登录或登录已过期</li>
 *   <li>403：无权限</li>
 *   <li>404：资源不存在</li>
 *   <li>405：方法不支持</li>
 *   <li>409：数据冲突（如重复支付/已激活）</li>
 *   <li>413：文件超限</li>
 *   <li>429：限流/幂等拦截</li>
 *   <li>500：系统繁忙（兜底）</li>
 * </ul>
 */
@Data
public class Result<T> {

    private boolean success;
    private String message;
    private T data;
    private int code;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.success = true;
        r.message = "success";
        r.data = data;
        r.code = 200;
        return r;
    }

    public static <T> Result<T> ok(T data, String message) {
        Result<T> r = new Result<>();
        r.success = true;
        r.message = message;
        r.data = data;
        r.code = 200;
        return r;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> r = new Result<>();
        r.success = false;
        r.message = message;
        r.code = 500;
        return r;
    }

    public static <T> Result<T> fail(String message, T data) {
        Result<T> r = new Result<>();
        r.success = false;
        r.message = message;
        r.data = data;
        r.code = 500;
        return r;
    }

    /**
     * 按业务码构造失败响应（推荐用法，便于前端按 code 分类处理）
     *
     * @param code    业务码（见类注释）
     * @param message 面向用户的友好提示
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.success = false;
        r.message = message;
        r.code = code;
        return r;
    }

    /**
     * 按业务码 + 数据构造失败响应（适用于需要在失败时回传数据的场景）
     */
    public static <T> Result<T> fail(int code, String message, T data) {
        Result<T> r = new Result<>();
        r.success = false;
        r.message = message;
        r.data = data;
        r.code = code;
        return r;
    }
}
