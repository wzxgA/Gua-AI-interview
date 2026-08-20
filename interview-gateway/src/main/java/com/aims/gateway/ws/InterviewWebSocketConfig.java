package com.aims.gateway.ws;

import com.aims.gateway.security.GuestTokenService;
import com.aims.gateway.security.JwtUtil;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.service.InterviewSessionService;
import io.jsonwebtoken.Claims;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/** WebSocket 配置：注册面试原生 WebSocket endpoint，握手时校验 JWT + 入口模式互斥。 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class InterviewWebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler handler;
    private final JwtUtil jwtUtil;
    private final InterviewSessionService sessionService;

    public InterviewWebSocketConfig(
            InterviewWebSocketHandler handler,
            JwtUtil jwtUtil,
            InterviewSessionService sessionService) {
        this.handler = handler;
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
    }

    /**
     * 定制 WebSocket 容器缓冲，避免出站文本消息超出 Tomcat 默认 8192 字节导致 1009 断连。
     *
     * <p>根因：最后一题回答收尾时若服务端回传一条超过 {@code maxTextMessageBufferSize} 的文本消息 （例如长异常信息），Tomcat 会在不支持分片时以
     * code=1009 直接关闭连接。因此把文本/二进制缓冲都调大。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean factory = new ServletServerContainerFactoryBean();
        // 文本缓冲调大（对齐 MAX_ANSWER_LENGTH 级别并留出余量，避免接近上限再次触发 1009）
        factory.setMaxTextMessageBufferSize(16 * 1024 * 1024);
        // 二进制缓冲调大（TTS 音频 / 图片等二进制消息）
        factory.setMaxBinaryMessageBufferSize(16 * 1024 * 1024);
        return factory;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/interview/{sessionId}")
                .addInterceptors(new TokenHandshakeInterceptor(jwtUtil, sessionService))
                .setAllowedOrigins("http://localhost:5173");
    }

    /**
     * 握手拦截器：从 query param 提取 token 校验，通过后将 username/role 放入 attributes。 同时按会话的 access_mode 做互斥校验：
     * CANDIDATE_ONLY 仅候选端可连，NONE/DISABLED 仅管理端可连。
     */
    static class TokenHandshakeInterceptor implements HandshakeInterceptor {

        private static final Logger log = LoggerFactory.getLogger(TokenHandshakeInterceptor.class);
        private final JwtUtil jwtUtil;
        private final InterviewSessionService sessionService;

        TokenHandshakeInterceptor(JwtUtil jwtUtil, InterviewSessionService sessionService) {
            this.jwtUtil = jwtUtil;
            this.sessionService = sessionService;
        }

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest req,
                ServerHttpResponse res,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes) {
            String query = req.getURI().getQuery();
            log.info("WebSocket 握手: uri={}, query={}", req.getURI(), query);
            if (query == null) {
                log.warn("WebSocket 握手失败: 无 query 参数");
                return false;
            }
            String token = null;
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    token = param.substring(6);
                    break;
                }
            }
            if (token == null) {
                log.warn("WebSocket 握手失败: 无 token 参数");
                return false;
            }
            try {
                Claims claims = jwtUtil.parse(token);
                String role = claims.get("role", String.class);
                Long pathSid = extractPathSessionId(req);
                boolean isGuest = "GUEST".equals(role);

                // GUEST（候选人）：校验 guestToken 绑定的 sid 与 URL 中 sessionId 一致
                if (isGuest) {
                    Long sid = GuestTokenService.extractSessionId(claims);
                    if (sid == null || !sid.equals(pathSid)) {
                        log.warn("WebSocket 握手失败: GUEST 会话不匹配");
                        return false;
                    }
                }

                // 入口模式互斥校验
                if (pathSid != null) {
                    InterviewSessionEntity entity = sessionService.getById(pathSid);
                    String accessMode = entity.getAccessMode();
                    if ("CANDIDATE_ONLY".equals(accessMode)) {
                        if (!isGuest) {
                            log.warn("WebSocket 握手拒绝: 会话已设为仅候选端，管理端不可连接 sessionId={}", pathSid);
                            return false;
                        }
                    } else {
                        // NONE / DISABLED：仅管理端可连
                        if (isGuest) {
                            log.warn("WebSocket 握手拒绝: 会话未开放候选端入口 sessionId={}", pathSid);
                            return false;
                        }
                    }
                }

                if (isGuest) {
                    attributes.put("role", "GUEST");
                    attributes.put("guestSessionId", pathSid);
                    log.info("WebSocket 握手成功: guestSessionId={}", pathSid);
                } else {
                    attributes.put("username", claims.getSubject());
                    attributes.put("role", role);
                    log.info("WebSocket 握手成功: username={}", claims.getSubject());
                }
                return true;
            } catch (Exception e) {
                log.warn("WebSocket 握手鉴权失败: {}", e.getMessage());
                return false;
            }
        }

        /** 从握手 URI 路径解析 sessionId（形如 /ws/interview/{sessionId}）。 */
        private Long extractPathSessionId(ServerHttpRequest req) {
            String path = req.getURI().getPath();
            String[] segments = path.split("/");
            if (segments.length == 0) {
                return null;
            }
            try {
                return Long.valueOf(segments[segments.length - 1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest req,
                ServerHttpResponse res,
                WebSocketHandler wsHandler,
                Exception exception) {
            // no-op
        }
    }
}
