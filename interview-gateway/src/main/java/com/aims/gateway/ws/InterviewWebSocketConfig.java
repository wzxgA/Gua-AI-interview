package com.aims.gateway.ws;

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
                attributes.put("username", claims.getSubject());
                attributes.put("role", claims.get("role", String.class));
                log.info("WebSocket 握手成功: username={}", claims.getSubject());
                return true;
            } catch (Exception e) {
                log.warn("WebSocket 握手鉴权失败: {}", e.getMessage());
                return false;
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
