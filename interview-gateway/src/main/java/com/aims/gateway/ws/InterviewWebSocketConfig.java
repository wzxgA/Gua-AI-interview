package com.aims.gateway.ws;

import com.aims.gateway.security.GuestTokenService;
import com.aims.gateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** WebSocket 配置：注册面试原生 WebSocket endpoint，握手时校验 JWT。 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class InterviewWebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler handler;
    private final JwtUtil jwtUtil;

    public InterviewWebSocketConfig(InterviewWebSocketHandler handler, JwtUtil jwtUtil) {
        this.handler = handler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/interview/{sessionId}")
                .addInterceptors(new TokenHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins("http://localhost:5173");
    }

    /** 握手拦截器：从 query param 提取 token 校验，通过后将 username 放入 attributes。 */
    static class TokenHandshakeInterceptor implements HandshakeInterceptor {

        private static final Logger log = LoggerFactory.getLogger(TokenHandshakeInterceptor.class);
        private final JwtUtil jwtUtil;

        TokenHandshakeInterceptor(JwtUtil jwtUtil) {
            this.jwtUtil = jwtUtil;
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
                // GUEST（候选人）：校验 guestToken 绑定的 sid 与 URL 中 sessionId 一致
                if ("GUEST".equals(role)) {
                    Long sid = GuestTokenService.extractSessionId(claims);
                    Long pathSid = extractPathSessionId(req);
                    if (sid == null || !sid.equals(pathSid)) {
                        log.warn("WebSocket 握手失败: GUEST 会话不匹配");
                        return false;
                    }
                    attributes.put("role", "GUEST");
                    attributes.put("guestSessionId", sid);
                    log.info("WebSocket 握手成功: guestSessionId={}", sid);
                    return true;
                }
                attributes.put("username", claims.getSubject());
                attributes.put("role", role);
                log.info("WebSocket 握手成功: username={}", claims.getSubject());
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
