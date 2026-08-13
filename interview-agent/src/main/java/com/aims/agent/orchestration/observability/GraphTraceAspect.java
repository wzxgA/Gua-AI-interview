package com.aims.agent.orchestration.observability;

import com.aims.agent.orchestration.node.AbstractNode;
import com.aims.agent.orchestration.node.FaultTolerantNode;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.common.NodeContextHolder;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Graph 节点执行切面：自动采集 9 个 Node 的耗时、入参/出参键、异常，注入 MDC。
 *
 * <p><b>切点设计</b>：两个互斥切点避免重复计数——
 *
 * <ol>
 *   <li>{@code FaultTolerantNode.apply}：生产主路径（{@link
 *       com.aims.agent.orchestration.graph.InterviewGraphFactory#wrap} 包装后），含重试循环总耗时
 *   <li>{@code AbstractNode+.apply}（排除 FaultTolerantNode）：兜底裸 Node 场景（测试 /
 *       compileWithoutCheckpoint）
 * </ol>
 *
 * <p>用 {@link #IN_TRACE} ThreadLocal 标记防重入（双切点互斥 + 内部 delegate 直接调用不被切）。
 *
 * <p><b>失败检测</b>：{@link FaultTolerantNode} 重试耗尽<b>不抛异常</b>，而是把 {@code LAST_ERROR} 写入 State 后返回。本切面在
 * {@code finally} 后检测返回 Map 是否含 {@code LAST_ERROR} key： 含则触发 {@code outcome=retry_exhausted} 失败指标 +
 * {@link GraphExecutionEvent.NodeFailed} 事件。
 *
 * <p><b>MDC 清理</b>：必须在 {@code finally} 中 {@link MDC#clear}，防止线程池复用污染。
 *
 * <p><b>反射容错</b>：从 FaultTolerantNode 反射读 {@code delegate} 字段名失败时降级为 {@code "unknown"}，不阻塞业务。
 *
 * @since 1.2.0 Phase 6
 */
@Aspect
@Component
public class GraphTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(GraphTraceAspect.class);

    private final GraphMetricsRegistry metrics;
    private final ApplicationEventPublisher eventPublisher;

    /** 防重入标记：当前线程已在 trace 中时跳过，避免双切点或内部自调用导致重复计数。 */
    private static final ThreadLocal<Boolean> IN_TRACE = ThreadLocal.withInitial(() -> false);

    public GraphTraceAspect(
            GraphMetricsRegistry metrics, ApplicationEventPublisher eventPublisher) {
        this.metrics = metrics;
        this.eventPublisher = eventPublisher;
    }

    /** 切 FaultTolerantNode（生产主路径，含重试循环总耗时）。 */
    @Around(
            "execution(* com.aims.agent.orchestration.node.FaultTolerantNode.apply(..)) &&"
                    + " args(state)")
    public Object traceFaultTolerant(ProceedingJoinPoint pjp, InterviewState state)
            throws Throwable {
        if (IN_TRACE.get()) {
            return pjp.proceed();
        }
        String nodeName = extractDelegateNodeName(pjp);
        return doTrace(pjp, state, nodeName);
    }

    /** 兜底：切未包装的 AbstractNode 子类（测试 / compileWithoutCheckpoint 场景）。 */
    @Around(
            "execution(* com.aims.agent.orchestration.node.AbstractNode+.apply(..)) && args(state)"
                + " && !execution(* com.aims.agent.orchestration.node.FaultTolerantNode.apply(..))")
    public Object traceRawNode(ProceedingJoinPoint pjp, InterviewState state) throws Throwable {
        if (IN_TRACE.get()) {
            return pjp.proceed();
        }
        String nodeName = extractNodeNameFromTarget(pjp);
        return doTrace(pjp, state, nodeName);
    }

    /** 实际 trace 逻辑：注入 MDC + 发布 NodeStarted + 执行 + 记录耗时/错误 + 清理。 */
    private Object doTrace(ProceedingJoinPoint pjp, InterviewState state, String nodeName)
            throws Throwable {
        IN_TRACE.set(true);
        NodeContextHolder.set(nodeName);

        String sessionId = state.sessionId() != null ? state.sessionId().toString() : "-";
        int round = state.currentSeq();

        MDC.put("sessionId", sessionId);
        MDC.put("node", nodeName);
        MDC.put("round", String.valueOf(round));

        eventPublisher.publishEvent(
                new GraphExecutionEvent.NodeStarted(sessionId, nodeName, round, Instant.now()));

        long start = System.nanoTime();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) pjp.proceed();
            long durationNanos = System.nanoTime() - start;
            long durationMs = durationNanos / 1_000_000;

            Set<String> outputKeys = result != null ? result.keySet() : Set.of();
            log.info(
                    "[TRACE] node={} duration={}ms output_keys={}",
                    nodeName,
                    durationMs,
                    outputKeys);

            metrics.recordNodeDuration(nodeName, durationNanos);

            // 检测重试耗尽：FaultTolerantNode 不抛异常，而是把 LAST_ERROR 写入返回 Map
            if (result != null && result.containsKey(InterviewState.LAST_ERROR)) {
                String errorMsg = String.valueOf(result.get(InterviewState.LAST_ERROR));
                metrics.incrementNodeError(nodeName, "retry_exhausted");
                eventPublisher.publishEvent(
                        new GraphExecutionEvent.NodeFailed(
                                sessionId,
                                nodeName,
                                round,
                                Instant.now(),
                                durationMs,
                                "retry_exhausted",
                                errorMsg));
            } else {
                eventPublisher.publishEvent(
                        new GraphExecutionEvent.NodeSucceeded(
                                sessionId, nodeName, round, Instant.now(), durationMs, outputKeys));
            }
            return result;

        } catch (Exception e) {
            long durationNanos = System.nanoTime() - start;
            long durationMs = durationNanos / 1_000_000;
            String errorType = e.getClass().getSimpleName();
            log.error(
                    "[TRACE] node={} duration={}ms ERROR: {} - {}",
                    nodeName,
                    durationMs,
                    errorType,
                    e.getMessage());

            metrics.recordNodeDuration(nodeName, durationNanos);
            metrics.incrementNodeError(nodeName, errorType);
            eventPublisher.publishEvent(
                    new GraphExecutionEvent.NodeFailed(
                            sessionId,
                            nodeName,
                            round,
                            Instant.now(),
                            durationMs,
                            errorType,
                            e.getMessage()));
            throw e;
        } finally {
            MDC.clear();
            NodeContextHolder.clear();
            IN_TRACE.set(false);
        }
    }

    /** 从 FaultTolerantNode 反射读 delegate.nodeName()；失败时降级为 "unknown"。 */
    private String extractDelegateNodeName(ProceedingJoinPoint pjp) {
        Object target = pjp.getTarget();
        if (target == null) {
            return "unknown";
        }
        try {
            Field delegateField = target.getClass().getDeclaredField("delegate");
            delegateField.setAccessible(true);
            Object delegate = delegateField.get(target);
            if (delegate instanceof AbstractNode<?> ab) {
                return ab.nodeName();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug(
                    "反射读 delegate.nodeName 失败 target={}，降级为 unknown",
                    target.getClass().getSimpleName());
        }
        return "unknown";
    }

    /** 从裸 Node target 读 nodeName()；非 AbstractNode 时降级为 "unknown"。 */
    private String extractNodeNameFromTarget(ProceedingJoinPoint pjp) {
        Object target = pjp.getTarget();
        if (target instanceof AbstractNode<?> ab) {
            return ab.nodeName();
        }
        return "unknown";
    }
}
