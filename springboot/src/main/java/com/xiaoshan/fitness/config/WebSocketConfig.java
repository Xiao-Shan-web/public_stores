package com.xiaoshan.fitness.config;

import com.xiaoshan.fitness.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

/**
 * WebSocket 配置（STOMP + SockJS + 内存代理）
 * <p>
 * 端点 /ws，握手时校验 JWT（Cookie 优先，回退 ?token= 查询参数），
 * CONNECT 时把身份映射为 Principal：user:{userId} / admin:{adminId}。
 * 聊天推送走内存代理（/queue），无需外部 STOMP 插件。
 * <p>
 * 在线连接计数 / Redis 在线标记 / 上下线广播统一由
 * {@link com.xiaoshan.fitness.service.WebSocketPresenceListener} 监听
 * SessionConnectedEvent / SessionDisconnectEvent 处理，本配置只负责设置 Principal。
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 WebSocket 端点，前端通过 /ws 连接（SockJS 兜底）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new StompHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 内存消息代理：客户端订阅 /user/queue/** 接收点对点消息
        registry.enableSimpleBroker("/topic", "/queue");
        // 应用前缀：客户端发送目的地 /app/service.send
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 握手阶段已把身份写入 attributes
                    java.util.Map<String, Object> attrs = accessor.getSessionAttributes();
                    if (attrs != null) {
                        Object userId = attrs.get(StompHandshakeInterceptor.ATTR_USER_ID);
                        Object userType = attrs.get(StompHandshakeInterceptor.ATTR_USER_TYPE);
                        if (userId != null && userType != null) {
                            // 设置 Principal（user:{id} / admin:{id}），供用户目的地路由使用。
                            // 连接计数 / Redis 在线标记 / 上下线广播由 WebSocketPresenceListener 处理。
                            String principalName = userType + ":" + userId;
                            accessor.setUser(new StompPrincipal(principalName));
                        }
                    }
                }
                return message;
            }
        });
    }

    /**
     * 握手拦截器：从 Cookie / ?token= 提取 JWT，校验后写入 WebSocket attributes
     */
    @RequiredArgsConstructor
    public class StompHandshakeInterceptor
            implements org.springframework.web.socket.server.HandshakeInterceptor {

        public static final String ATTR_USER_ID = "wsUserId";
        public static final String ATTR_USER_TYPE = "wsUserType";

        @Override
        public boolean beforeHandshake(
                org.springframework.http.server.ServerHttpRequest request,
                org.springframework.http.server.ServerHttpResponse response,
                org.springframework.web.socket.WebSocketHandler wsHandler,
                java.util.Map<String, Object> attributes) {

            // 1. Cookie 优先（H5 同源场景，浏览器自动携带）
            String token = null;
            if (request instanceof org.springframework.http.server.ServletServerHttpRequest servletRequest) {
                token = jwtUtil.extractToken(servletRequest.getServletRequest());
            }
            // 2. 回退 ?token= 查询参数
            if (token == null) {
                var params = org.springframework.web.util.UriComponentsBuilder
                        .fromUri(request.getURI()).build().getQueryParams();
                token = params.getFirst("token");
            }

            if (token == null || !jwtUtil.validateToken(token)) {
                // 未认证直接拒绝握手
                return false;
            }

            attributes.put(ATTR_USER_ID, jwtUtil.getUserIdFromToken(token));
            attributes.put(ATTR_USER_TYPE, jwtUtil.getTypeFromToken(token));
            return true;
        }

        @Override
        public void afterHandshake(
                org.springframework.http.server.ServerHttpRequest request,
                org.springframework.http.server.ServerHttpResponse response,
                org.springframework.web.socket.WebSocketHandler wsHandler,
                Exception exception) {
            // 无需处理
        }
    }

    /**
     * 简单 Principal 实现（name 形如 user:123 / admin:1）
     */
    public static class StompPrincipal implements Principal {

        private final String name;

        public StompPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

}
