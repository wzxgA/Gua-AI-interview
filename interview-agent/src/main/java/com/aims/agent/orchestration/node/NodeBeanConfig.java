package com.aims.agent.orchestration.node;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 编排节点 Bean 配置。
 *
 * <p>为 {@link StreamEmitter} 注册默认的 NOOP 实现，保证 Phase 2–4 阶段（WebSocket 推送尚未接入）应用可正常启动。 Phase 5 Engine
 * 层注册真实的 {@code WebSocketStreamEmitter} Bean 后，本默认实现会被自动覆盖。
 *
 * @since 1.1.0
 */
@Configuration
public class NodeBeanConfig {

    /** 默认流式推送实现：丢弃所有 chunk；容器中存在其他 StreamEmitter Bean 时自动让位。 */
    @Bean
    @ConditionalOnMissingBean
    public StreamEmitter streamEmitter() {
        return StreamEmitter.NOOP;
    }
}
