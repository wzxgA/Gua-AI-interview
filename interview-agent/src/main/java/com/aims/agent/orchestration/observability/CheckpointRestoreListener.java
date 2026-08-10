package com.aims.agent.orchestration.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听 {@link GraphExecutionEvent.CheckpointRestored} 事件，触发 {@code aims.graph.checkpoint.restore} 计数。
 *
 * <p>事件由 {@link com.aims.gateway.orchestration.InterviewWorkflowEngine} 在从 Redis checkpoint 加载到非空
 * state 时发布。
 *
 * @since 1.2.0 Phase 6
 */
@Component
public class CheckpointRestoreListener {

    private static final Logger log = LoggerFactory.getLogger(CheckpointRestoreListener.class);

    private final GraphMetricsRegistry metrics;

    public CheckpointRestoreListener(GraphMetricsRegistry metrics) {
        this.metrics = metrics;
    }

    @EventListener
    public void onCheckpointRestored(GraphExecutionEvent.CheckpointRestored event) {
        metrics.incrementCheckpointRestore(event.restoreSource());
        log.info(
                "[OBS] checkpoint restored session={} source={}",
                event.sessionId(),
                event.restoreSource());
    }
}
