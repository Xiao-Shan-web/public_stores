package com.xiaoshan.springbootdemo.controller;

import com.xiaoshan.springbootdemo.entity.User;
import com.xiaoshan.springbootdemo.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户端控制器
 * 提供用户认证、订单创建、支付等接口
 *
 * @author xiaoshan
 * @date 2026-05-06
 */
@Slf4j
@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ==================== 用户认证 ====================

    /**
     * 用户认证接口
     * <p>
     * 根据设备ID验证用户身份，如果用户不存在则自动创建新用户。
     *
     * @param deviceId 设备唯一标识
     * @return 认证结果，包含用户ID
     */
    @GetMapping("/auth/{deviceId}")
    public ResponseEntity<?> deviceLogin(@PathVariable("deviceId") String deviceId) {
        try {
            Long userId = userService.getUserId(deviceId);

            if (userId != null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "校验成功",
                        "data", Map.of("userId", userId)
                ));
            } else {
                return ResponseEntity.ok().body(Map.of(
                        "success", false,
                        "message", "校验失败"

                ));
            }
        } catch (Exception e) {
            log.error("用户认证失败", e);
            return ResponseEntity.ok().body(Map.of(
                    "success", false,
                    "message", "系统错误"

            ));
        }
    }

    /**
     * 绑定手机号
     * @param userId 用户ID
     * @param request 请求体
     * @return 绑定结果
     */
    @PutMapping("/user/{userId}/phone")
    public ResponseEntity<?> bindPhone(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        try {
            String phone = request.get("phone");

            // 校验手机号格式
            if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
                return ResponseEntity.ok().body(Map.of(
                        "success", false,
                        "message", "请输入正确的手机号"
                ));
            }

            boolean success = userService.updateUserPhone(userId, phone);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "手机号绑定成功"
                ));
            } else {
                return ResponseEntity.ok().body(Map.of(
                        "success", false,
                        "message", "手机号已被其他账号绑定"
                ));
            }
        } catch (Exception e) {
            log.error("绑定手机号失败", e);
            return ResponseEntity.ok().body(Map.of(
                    "success", false,
                    "message", "绑定失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 通过手机号查询用户ID（找回账号）
     * @param phone 手机号
     * @return 用户ID
     */
    @GetMapping("/user/findByPhone")
    public ResponseEntity<?> findUserIdByPhone(@RequestParam String phone) {
        try {
            // 校验手机号格式
            if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
                return ResponseEntity.ok().body(Map.of(
                        "success", false,
                        "message", "请输入正确的手机号"
                ));
            }

            Long userId = userService.getUserIdByPhone(phone);

            if (userId != null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "data", Map.of("userId", userId)
                ));
            } else {
                return ResponseEntity.ok().body(Map.of(
                        "success", false,
                        "message", "未找到该手机号绑定的账号"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.ok().body(Map.of(
                    "success", false,
                    "message", "查询失败"
            ));
        }
    }

    /**
     * 获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserInfo(@PathVariable Long userId) {
        try {
            User user = userService.findById(userId);

            if (user != null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "data", Map.of(
                            "userId", user.getId(),
                            "phone", user.getPhone() != null ? user.getPhone() : ""
                        )
                ));
            } else {
                return ResponseEntity.ok().body(Map.of(
                        "success", false,
                        "message", "用户不存在"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.ok().body(Map.of(
                    "success", false,
                    "message", "获取失败"
            ));
        }
    }



}