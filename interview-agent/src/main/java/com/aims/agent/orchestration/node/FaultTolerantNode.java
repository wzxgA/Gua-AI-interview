package com.aims.agent.orchestration.node;

import com.aims.agent.orchestration.observability.GraphMetricsRegistry;
import com.aims.agent.orchestration.state.InterviewState;
import java.util.HashMap;
import java.util.Map;
import org.bsc.langgraph4j.action.NodeAction;

/**
 * 容错节点装饰器：为 NodeAction 提供重试与异常处理。
 *
 * <p>重试策略：指数退避（baseDelayMs * 2^(attempt-1)）。重试耗尽后不抛异常，将错误写入 State（LAST_ERROR + RETRY_COUNT），保证
 * Graph 不中断。Phase 3 条件边可检查 LAST_ERROR 决定路由。
 *
 * <p>Phase 6 新增：可选注入 {@link GraphMetricsRegistry}，在每次重试时埋点 {@code aims.graph.node.retry}
 * 指标。为向后兼容，构造函数保留不带 metrics 的重载，{@link #metricsRegistry} 可为 null。
 *
 * @param <S> 状态类型
 * @since 1.1.0
 */
public class FaultTolerantNode<S extends InterviewState> implements NodeAction<S> {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_BASE_DELAY_MS = 500;

    private final NodeAction<S> delegate;
    private final int maxRetries;
    private final long baseDelayMs;

    /** Phase 6 注入：可选的指标注册中心，为 null 时不埋点（向后兼容测试用例）。 */
    private final GraphMetricsRegistry metricsRegistry;

    public FaultTolerantNode(NodeAction<S> delegate) {
        this(delegate, DEFAULT_MAX_RETRIES, DEFAULT_BASE_DELAY_MS, null);
    }

    public FaultTolerantNode(NodeAction<S> delegate, int maxRetries, long baseDelayMs) {
        this(delegate, maxRetries, baseDelayMs, null);
    }

    /**
     * Phase 6 构造函数：注入 {@link GraphMetricsRegistry} 用于重试埋点。
     *
     * @param metricsRegistry 可为 null（测试场景），非 null 时在每次重试前埋点
     */
    public FaultTolerantNode(
            NodeAction<S> delegate,
            int maxRetries,
            long baseDelayMs,
            GraphMetricsRegistry metricsRegistry) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public Map<String, Object> apply(S state) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return delegate.apply(state);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries) {
                    // Phase 6：重试埋点（仅当 metricsRegistry 非空）
                    if (metricsRegistry != null) {
                        metricsRegistry.incrementNodeRetry(delegateNodeName());
                    }
                    long delay = baseDelayMs * (1L << (attempt - 1));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }
        // 重试耗尽：写入错误，不抛异常
        Map<String, Object> errorUpdate = new HashMap<>();
        errorUpdate.put(
                InterviewState.LAST_ERROR,
                lastException != null ? lastException.getMessage() : "unknown error");
        errorUpdate.put(InterviewState.RETRY_COUNT, maxRetries);
        return errorUpdate;
    }

    /** 尝试反射读取 delegate 的 nodeName，用于重试指标 tag；失败降级为 "unknown"。 */
    private String delegateNodeName() {
        if (delegate instanceof AbstractNode<?> ab) {
            return ab.nodeName();
        }
        return "unknown";
    }
}
