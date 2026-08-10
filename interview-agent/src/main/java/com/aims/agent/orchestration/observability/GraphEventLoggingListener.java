package com.aims.agent.orchestration.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 将 {@link GraphExecutionEvent} 落结构化日志，便于 ELK / Loki 采集。
 *
 * <p>Phase 6 仅落日志；P6 前端流程可视化可通过订阅这些事件并经 WebSocket 推送。
 *
 * <p>日志前缀 {@code [GRAPH]}，与 {@link GraphTraceAspect} 的 {@code [TRACE]} 区分： {@code [TRACE]}
 * 是节点级耗时记录，{@code [GRAPH]} 是事件总线广播记录。
 *
 * @since 1.2.0 Phase 6
 */
@Component
public class GraphEventLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(GraphEventLoggingListener.class);

    @EventListener
    public void onNodeStarted(GraphExecutionEvent.NodeStarted e) {
        log.info("[GRAPH] START session={} node={} round={}", e.sessionId(), e.node(), e.round());
    }

    @EventListener
    public void onNodeSucceeded(GraphExecutionEvent.NodeSucceeded e) {
        log.info(
                "[GRAPH] DONE  session={} node={} round={} duration={}ms outputs={}",
                e.sessionId(),
                e.node(),
                e.round(),
                e.durationMs(),
                e.outputKeys());
    }

    @EventListener
    public void onNodeFailed(GraphExecutionEvent.NodeFailed e) {
        log.error(
                "[GRAPH] FAIL session={} node={} round={} duration={}ms error={} msg={}",
                e.sessionId(),
                e.node(),
                e.round(),
                e.durationMs(),
                e.errorType(),
                e.errorMessage());
    }
}
