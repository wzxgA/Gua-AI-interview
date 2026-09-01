package com.aims.agent.orchestration.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aims.agent.orchestration.graph.NodeNames;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link GraphMetricsRegistry} 单元测试：验证各 {@code aims.graph.*} 指标正确注册与计数。
 *
 * @since 1.2.0 Phase 6
 */
class GraphMetricsRegistryTest {

    private SimpleMeterRegistry meterRegistry;
    private GraphMetricsRegistry graphMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        graphMetrics = new GraphMetricsRegistry(meterRegistry);
        graphMetrics.initGauges();
    }

    @Test
    @DisplayName("recordNodeDuration: 注册 Timer 并记录耗时")
    void recordNodeDuration_registersTimerAndRecords() {
        graphMetrics.recordNodeDuration("ask", 1_000_000_000L); // 1s in nanos

        Timer timer = meterRegistry.find("aims.graph.node.duration").tag("node", "ask").timer();
        assertTrue(timer != null, "Timer 应已注册");
        assertEquals(1, timer.count(), "应记录 1 次");
    }

    @Test
    @DisplayName("recordNodeDuration: report 节点（异步报告）不记耗时，但 error/retry 仍统计")
    void recordNodeDuration_excludesReportNode() {
        graphMetrics.recordNodeDuration(NodeNames.REPORT, 1_000_000_000L);
        graphMetrics.incrementNodeError(NodeNames.REPORT, "AiException");
        graphMetrics.incrementNodeRetry(NodeNames.REPORT);

        assertTrue(
                meterRegistry.find("aims.graph.node.duration").tag("node", NodeNames.REPORT).timer()
                        == null,
                "report 节点不应注册耗时 Timer");
        assertEquals(
                1.0,
                meterRegistry
                        .find("aims.graph.node.error")
                        .tag("node", NodeNames.REPORT)
                        .counter()
                        .count(),
                "report 节点错误仍应统计");
        assertEquals(
                1.0,
                meterRegistry
                        .find("aims.graph.node.retry")
                        .tag("node", NodeNames.REPORT)
                        .counter()
                        .count(),
                "report 节点重试仍应统计");
    }

    @Test
    @DisplayName("incrementNodeError: 按 node+error_type 聚合计数")
    void incrementNodeError_aggregatesByNodeAndErrorType() {
        graphMetrics.incrementNodeError("plan", "AiException");
        graphMetrics.incrementNodeError("plan", "AiException");
        graphMetrics.incrementNodeError("ask", "RuntimeException");

        assertEquals(
                2.0,
                meterRegistry
                        .find("aims.graph.node.error")
                        .tag("node", "plan")
                        .tag("error_type", "AiException")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                meterRegistry
                        .find("aims.graph.node.error")
                        .tag("node", "ask")
                        .tag("error_type", "RuntimeException")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("incrementNodeRetry: 按 node 聚合重试计数")
    void incrementNodeRetry_aggregatesByNode() {
        graphMetrics.incrementNodeRetry("evaluate");
        graphMetrics.incrementNodeRetry("evaluate");

        assertEquals(
                2.0,
                meterRegistry
                        .find("aims.graph.node.retry")
                        .tag("node", "evaluate")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("incrementCheckpointRestore: 按 source 聚合恢复计数")
    void incrementCheckpointRestore_aggregatesBySource() {
        graphMetrics.incrementCheckpointRestore("checkpoint");
        graphMetrics.incrementCheckpointRestore("db_rebuild");
        graphMetrics.incrementCheckpointRestore("checkpoint");

        assertEquals(
                2.0,
                meterRegistry
                        .find("aims.graph.checkpoint.restore")
                        .tag("source", "checkpoint")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                meterRegistry
                        .find("aims.graph.checkpoint.restore")
                        .tag("source", "db_rebuild")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("updateCurrentRound: Gauge 反映当前 seq 与 total")
    void updateCurrentRound_updatesGauges() {
        graphMetrics.updateCurrentRound(3, 8);

        assertEquals(3, meterRegistry.find("aims.graph.round.current").gauge().value());
        assertEquals(8, meterRegistry.find("aims.graph.round.total").gauge().value());
    }
}
