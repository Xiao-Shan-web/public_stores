package com.xiaoshan.fitness.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 原则：
 * 1. 返回给前端的 message 仅包含友好提示，不含 SQL/堆栈/类名/字段名/内部错误码
 * 2. 完整异常信息只写入日志（log.error），便于排查
 * 3. 统一返回格式：{ success, message, code }，code 与 Result 对齐
 * <p>
 * 业务码分类（与 Result 一致）：
 * <ul>
 *   <li>400：业务/参数错误（BusinessException 默认、参数校验失败、JSON 解析失败、缺参）</li>
 *   <li>401：未登录或登录已过期（AuthenticationException）</li>
 *   <li>403：无权限（AccessDeniedException）</li>
 *   <li>404：资源不存在（NoHandlerFoundException）</li>
 *   <li>405：方法不支持（HttpRequestMethodNotSupportedException）</li>
 *   <li>409：数据冲突（SQL 约束冲突）</li>
 *   <li>413：文件超限</li>
 *   <li>429：限流/幂等拦截（由幂等拦截器抛出 BusinessException(429, ...)）</li>
 *   <li>500：系统繁忙（兜底）</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 构建强制 JSON响应头（避免被异常页面或浏览器嗅探干扰）
     */
    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.inline().build());
        return headers;
    }

    /**
     * 构建统一响应体
     */
    private Map<String, Object> body(boolean success, String message, int code) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("code", code);
        return response;
    }

    /**
     * 业务异常：返回业务错误信息（安全友好提示）+ 业务码
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException ex) {
        log.warn("业务异常[code={}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.ok()
                .headers(buildJsonHeaders())
                .body(body(false, ex.getMessage(), ex.getCode()));
    }

    /**
     * 参数校验失败（@Valid）：统一返回"参数格式不正确"，不暴露字段名
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("参数校验失败: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .headers(buildJsonHeaders())
                .body(body(false, "参数格式不正确", 400));
    }

    /**
     * Bean 校验失败（@RequestParam / @PathVariable 上的 @Validated）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("约束校验失败: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .headers(buildJsonHeaders())
                .body(body(false, "参数格式不正确", 400));
    }

    /**
     * 请求体格式错误（JSON 解析失败）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体格式错误: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .headers(buildJsonHeaders())
                .body(body(false, "参数格式不正确", 400));
    }

    /**
     * 缺少必要请求参数：不暴露参数名
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("缺少请求参数: {}", ex.getParameterName());
        return ResponseEntity.badRequest()
                .headers(buildJsonHeaders())
                .body(body(false, "参数格式不正确", 400));
    }

    /**
     * 非法参数：业务代码主动抛出的参数错误
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("非法参数: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .headers(buildJsonHeaders())
                .body(body(false, "参数格式不正确", 400));
    }

    /**
     * 未登录或登录已过期
     * 注意：SecurityConfig 当前全放行，主要由 JwtAuthenticationFilter 主动抛出
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthentication(AuthenticationException ex) {
        log.warn("认证失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .headers(buildJsonHeaders())
                .body(body(false, "登录已过期，请重新登录", 401));
    }

    /**
     * 无权限访问
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        log.warn("权限不足: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .headers(buildJsonHeaders())
                .body(body(false, "无权限操作", 403));
    }

    /**
     * 资源不存在（404）：需开启 spring.mvc.throw-exception-if-no-handler-found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> handleNotFound(NoHandlerFoundException ex) {
        log.warn("资源不存在: {}", ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .headers(buildJsonHeaders())
                .body(body(false, "资源不存在", 404));
    }

    /**
     * 静态资源不存在（Boot 3.2+ / 4.x 默认抛出）
     * 常见于 favicon.ico、爬虫探测路径等，属正常现象：
     * 只记 DEBUG，避免污染 ERROR 日志，也绝不按 500 "系统繁忙" 返回
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("静态资源不存在: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .headers(buildJsonHeaders())
                .body(body(false, "资源不存在", 404));
    }

    /**
     * 请求方法不支持（405）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("方法不支持: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(buildJsonHeaders())
                .body(body(false, "请求方法不支持", 405));
    }

    /**
     * SQL 约束冲突：不泄露 SQL 语句/约束名
     * 用 409 表示状态冲突（重复键、外键冲突等）
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ResponseEntity<?> handleSQLConstraintViolation(SQLIntegrityConstraintViolationException ex) {
        log.error("数据约束冲突", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .headers(buildJsonHeaders())
                .body(body(false, "操作失败，数据冲突", 409));
    }

    /**
     * 文件上传大小超限
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("文件上传超限: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .headers(buildJsonHeaders())
                .body(body(false, "文件大小超过限制", 413));
    }

    /**
     * 客户端连接断开（正常行为，不记录 ERROR）
     */
    @ExceptionHandler({ClosedChannelException.class, IOException.class})
    public ResponseEntity<?> handleClientDisconnect(Exception ex) {
        String message = ex.getMessage();
        if (message != null && (message.contains("Connection reset by peer") ||
                message.contains("Broken pipe") ||
                message.contains("ClientAbortException"))) {
            log.debug("客户端断开连接: {}", message);
        } else if (ex instanceof ClosedChannelException) {
            log.debug("通道已关闭（客户端断开）");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 其他运行时异常：统一返回"系统繁忙"，不返回 ex.getMessage()
     * 完整异常仅记录到日志
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();
        // 客户端断开导致的运行时异常，静默处理
        if (message != null && (message.contains("Connection reset") ||
                message.contains("ClosedChannelException") ||
                message.contains("Broken pipe"))) {
            log.debug("客户端断开导致的运行时异常: {}", message);
            return ResponseEntity.noContent().build();
        }
        log.error("运行时异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .headers(buildJsonHeaders())
                .body(body(false, "系统繁忙，请稍后重试", 500));
    }

    /**
     * 未捕获异常兜底：统一返回"系统繁忙"
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        if (ex instanceof IOException || ex instanceof ClosedChannelException) {
            return ResponseEntity.noContent().build();
        }
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .headers(buildJsonHeaders())
                .body(body(false, "系统繁忙，请稍后重试", 500));
    }

}
