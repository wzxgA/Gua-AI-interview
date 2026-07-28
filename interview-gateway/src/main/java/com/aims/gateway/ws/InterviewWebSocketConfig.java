package com.aims.gateway.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WebSocket 配置：注册面试原生 WebSocket endpoint。 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class InterviewWebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler handler;

    public InterviewWebSocketConfig(InterviewWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/interview/{sessionId}").setAllowedOrigins("*");
    }
}
