package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.entity.CustomerServiceMessage;
import com.xiaoshan.fitness.entity.User;
import com.xiaoshan.fitness.mapper.CustomerServiceMessageMapper;
import com.xiaoshan.fitness.mapper.UserMapper;
import com.xiaoshan.fitness.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客服聊天服务：消息落库 + WebSocket 实时推送
 * <p>
 * 推送约定（STOMP 载荷统一为 JSON 字符串）：
 * - 目的地：/user/{principalName}/queue/service（principalName = user:{id} / admin:{id}）
 * - 用户发消息 → 只扇出给所有在线客服，不回推给发送者本人
 * - 客服回消息 → 只推给目标用户，不回推给发送客服本人
 * （发送方自己发出的消息由前端本地乐观上屏，无需服务端回显）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceChatService {

    /** 消息内容最大长度 */
    public static final int MAX_CONTENT_LENGTH = 500;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CustomerServiceMessageMapper messageMapper;
    private final UserMapper userMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final SimpMessagingTemplate messagingTemplate;
    private final OnlineStatusService onlineStatusService;

    /**
     * 用户发送消息
     *
     * @return 消息 VO（已落库，含 id/createdAt）
     */
    public Map<String, Object> sendUserMessage(Long userId, String content) {
        String text = validateContent(content);
        CustomerServiceMessage msg = new CustomerServiceMessage();
        msg.setId(idGenerator.nextId());
        msg.setUserId(userId);
        msg.setSenderType(CustomerServiceMessage.SENDER_USER);
        msg.setContent(text);
        msg.setIsRead(0);
        messageMapper.insert(msg);

        Map<String, Object> vo = buildVO(msg, userPhone(userId), userId);
        // 只扇出给所有在线客服，不回推给发送者本人（前端本地已上屏）
        for (String adminPrincipal : onlineStatusService.connectedAdminPrincipals()) {
            messagingTemplate.convertAndSendToUser(adminPrincipal, "/queue/service", toJson(vo));
        }
        log.info("用户{}发送客服消息 msgId={}", userId, msg.getId());
        return vo;
    }

    /**
     * 客服回复消息
     *
     * @param adminId  发送客服ID
     * @param toUserId 目标用户ID
     * @return 消息 VO（已落库，含 id/createdAt）
     */
    public Map<String, Object> sendAdminMessage(Long adminId, Long toUserId, String content) {
        String text = validateContent(content);
        CustomerServiceMessage msg = new CustomerServiceMessage();
        msg.setId(idGenerator.nextId());
        msg.setUserId(toUserId);
        msg.setSenderType(CustomerServiceMessage.SENDER_ADMIN);
        msg.setContent(text);
        msg.setIsRead(0);
        messageMapper.insert(msg);

        Map<String, Object> vo = buildVO(msg, userPhone(toUserId), adminId);
        // 只推给目标用户，不回推给发送客服本人（前端本地已上屏）
        messagingTemplate.convertAndSendToUser("user:" + toUserId, "/queue/service", toJson(vo));
        log.info("客服{}回复用户{}消息 msgId={}", adminId, toUserId, msg.getId());
        return vo;
    }

    /**
     * 构建消息视图对象
     *
     * @param senderId 发送者ID（USER 消息为用户ID，ADMIN 消息为客服ID；历史消息中客服ID可能为 null）
     */
    public Map<String, Object> buildVO(CustomerServiceMessage msg, String phone, Long senderId) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", msg.getId());
        vo.put("userId", msg.getUserId());
        vo.put("senderId", senderId);
        vo.put("phone", phone);
        vo.put("senderType", msg.getSenderType());
        vo.put("content", msg.getContent());
        // TINYINT 统一以 0/1 整型下发，前端用 Number(isRead) === 1 判断，避免布尔误判
        vo.put("isRead", msg.getIsRead() == null ? 0 : msg.getIsRead());
        vo.put("createdAt", msg.getCreatedAt() != null
                ? msg.getCreatedAt().format(TIME_FORMATTER) : null);
        return vo;
    }

    /**
     * 内容基础校验：非空且不超过 500 字
     */
    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        String text = content.trim();
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("消息内容不能超过" + MAX_CONTENT_LENGTH + "字");
        }
        return text;
    }

    private String userPhone(Long userId) {
        return userMapper.findById(userId).map(User::getPhone).orElse(null);
    }

    private String toJson(Map<String, Object> vo) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(vo);
        } catch (Exception e) {
            log.error("消息序列化失败", e);
            return "{}";
        }
    }

}
