package com.aims.agent.orchestration.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Graph 指标注册中心：封装所有 {@code aims.graph.*} 指标的注册与记录。
 *
 * <p>所有指标严格遵循 {@link package-info} 中的标签基数闭集策略，sessionId 不进入 metric tag。
 *
 * <p>Gauge 使用 {@link AtomicInteger} 持有引用，{@link #initGauges} 注册一次，后续通过 {@link #updateCurrentRound}
 * 动态更新 value，避免重复注册异常。
 *
 * @since 1.2.0 Phase 6
 */
@Component
public class GraphMetricsRegistry {

    private final MeterRegistry meterRegistry;

    private final AtomicInteger currentRoundValue = new AtomicInteger(0);
    private final AtomicInteger totalRoundsValue = new AtomicInteger(0);

    public GraphMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void initGauges() {
        meterRegistry.gauge("aims.graph.round.current", currentRoundValue);
        meterRegistry.gauge("aims.graph.round.total", totalRoundsValue);
    }

    // ─── aims.graph.node.duration (Timer) ───

    /** 记录节点执行耗时。 */
    public void recordNodeDuration(String node, long durationNanos) {
        meterRegistry
                .timer("aims.graph.node.duration", "node", node)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    // ─── aims.graph.node.error (Counter) ───

    /** 记录节点错误（按 error_type 聚合）。 */
    public void incrementNodeError(String node, String errorType) {
        meterRegistry
                .counter("aims.graph.node.error", "node", node, "error_type", errorType)
                .increment();
    }

    // ─── aims.graph.node.retry (Counter) ───

    /** 记录节点重试次数。 */
    public void incrementNodeRetry(String node) {
        meterRegistry.counter("aims.graph.node.retry", "node", node).increment();
    }

    // ─── aims.graph.checkpoint.restore (Counter) ───

    /** 记录 checkpoint 恢复次数。 */
    public void incrementCheckpointRestore(String restoreSource) {
        meterRegistry.counter("aims.graph.checkpoint.restore", "source", restoreSource).increment();
    }

    // ─── aims.graph.execution (Counter) ───

    /** 记录 Engine 入口调用。 */
    public void incrementExecution(String entrypoint, String outcome) {
        meterRegistry
                .counter(
                        "aims.graph.execution",
                        Tags.of("entrypoint", entrypoint, "outcome", outcome))
                .increment();
    }

    // ─── aims.graph.round.current / total (Gauge) ───

    /** 更新当前面试进度 Gauge。 */
    public void updateCurrentRound(int seq, int total) {
        currentRoundValue.set(seq);
        totalRoundsValue.set(total);
    }
}
