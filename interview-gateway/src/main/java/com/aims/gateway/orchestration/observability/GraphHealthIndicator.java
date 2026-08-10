package com.aims.gateway.orchestration.observability;

import com.aims.gateway.orchestration.InterviewWorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * Graph 子系统健康检查：将 Graph 编排子系统纳入 {@code /actuator/health} 的 {@code graph} 子项。
 *
 * <p>用 {@link ObjectProvider} 避免 Graph 未初始化时（如单元测试上下文、灰度开关关闭）启动失败。
 *
 * <p>检查项：
 *
 * <ul>
 *   <li>CompiledGraph 是否已编译（{@link InterviewWorkflowEngine#isGraphReady()}）
 *   <li>checkpoint 后端（Redis）是否可用（{@link InterviewWorkflowEngine#isCheckpointBackendAvailable()}）
 * </ul>
 *
 * <p>状态：
 *
 * <ul>
 *   <li>Graph 就绪 + checkpoint 可用 → {@code UP}
 *   <li>Graph 未就绪（灰度关闭 / 未初始化）→ {@code OUT_OF_SERVICE}（或 {@code DOWN}，此处选 {@code OUT_OF_SERVICE}
 *       表示功能未启用而非系统故障）
 * </ul>
 *
 * <p>注意：本类放在 {@code interview-gateway}（而非计划草稿的 {@code interview-agent}）， 因 CompiledGraph 是 Engine
 * 的私有字段，需要 Engine 暴露就绪状态。
 *
 * @since 1.2.0 Phase 6
 */
@Component
public class GraphHealthIndicator extends AbstractHealthIndicator {

    private final ObjectProvider<InterviewWorkflowEngine> engineProvider;

    public GraphHealthIndicator(ObjectProvider<InterviewWorkflowEngine> engineProvider) {
        this.engineProvider = engineProvider;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        InterviewWorkflowEngine engine = engineProvider.getIfAvailable();
        if (engine == null) {
            builder.down().withDetail("error", "InterviewWorkflowEngine bean not present");
            return;
        }

        boolean graphReady = engine.isGraphReady();
        boolean checkpointAvailable = engine.isCheckpointBackendAvailable();
        boolean enabled = engine.isEnabled();

        builder.withDetail("enabled", enabled);
        builder.withDetail("compiled", graphReady ? "ready" : "missing");
        builder.withDetail("checkpointBackend", checkpointAvailable ? "redis" : "none");

        if (graphReady && checkpointAvailable) {
            builder.up();
        } else if (!enabled) {
            // 灰度开关关闭：功能未启用，不算系统故障
            builder.outOfService().withDetail("reason", "engine disabled (grayscale off)");
        } else {
            builder.down().withDetail("error", "graph or checkpoint not ready");
        }
    }
}
