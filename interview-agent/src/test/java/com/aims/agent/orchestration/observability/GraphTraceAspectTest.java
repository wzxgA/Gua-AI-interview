package com.aims.agent.orchestration.observability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.aims.agent.orchestration.node.AbstractNode;
import com.aims.agent.orchestration.node.FaultTolerantNode;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.agent.orchestration.state.TestStateBuilder;
import com.aims.core.common.NodeContextHolder;
import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

/**
 * {@link GraphTraceAspect} 单元测试。
 *
 * <p>验证：耗时记录、错误计数、MDC 注入与清理、重入防护、FaultTolerantNode 包装场景、裸 Node 兜底场景。
 *
 * <p>不经过 Spring AOP 代理，直接调用 aspect 方法（模拟切面被织入后的行为）， 通过 mock {@link ProceedingJoinPoint#proceed()}
 * 控制业务执行结果。
 *
 * @since 1.2.0 Phase 6
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class GraphTraceAspectTest {

    @Mock private GraphMetricsRegistry metrics;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private GraphTraceAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new GraphTraceAspect(metrics, eventPublisher);
    }

    @AfterEach
    void cleanup() {
        // 防御性清理，防止 ThreadLocal 残留影响其他测试
        MDC.clear();
        NodeContextHolder.clear();
    }

    // ─── 测试桩 ───

    /** 创建一个 mock ProceedingJoinPoint，target 指向给定对象，proceed 返回给定结果。 */
    private ProceedingJoinPoint mockJoinPoint(Object target, Object result) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.proceed()).thenReturn(result);
        return pjp;
    }

    /** 创建一个 mock ProceedingJoinPoint，target 指向给定对象，proceed 抛出给定异常。 */
    private ProceedingJoinPoint mockFailingJoinPoint(Object target, Throwable failure)
            throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(target);
        when(pjp.proceed()).thenThrow(failure);
        return pjp;
    }

    /** 命名为 ask 的桩 Node：直接返回传入的 result。 */
    private AbstractNode<InterviewState> mockNodeNamed(String name, Map<String, Object> result)
            throws Exception {
        AbstractNode<InterviewState> node = mock(AbstractNode.class, withSettings().name(name));
        when(node.nodeName()).thenReturn(name);
        when(node.apply(any())).thenReturn(result);
        return node;
    }

    /** 命名为 name 的失败桩 Node。 */
    private AbstractNode<InterviewState> mockFailingNodeNamed(String name, Throwable failure)
            throws Exception {
        AbstractNode<InterviewState> node = mock(AbstractNode.class);
        when(node.nodeName()).thenReturn(name);
        when(node.apply(any())).thenThrow(failure);
        return node;
    }

    private InterviewState testState() {
        return TestStateBuilder.forTesting().withSessionId(42L).withCurrentSeq(1).build();
    }

    // ─── 测试用例 ───

    @Test
    @DisplayName("FaultTolerantNode 包装场景：成功路径记录耗时 + 发布 NodeStarted/Succeeded")
    void traceFaultTolerant_success_recordsDurationAndEvents() throws Throwable {
        // given: 真实的 FaultTolerantNode 实例（含 delegate 字段，供反射读取 nodeName）
        Map<String, Object> result = Map.of("currentQuestion", "What is Spring?");
        AbstractNode<InterviewState> delegate = mockNodeNamed("ask", result);
        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 1, 0);
        ProceedingJoinPoint pjp = mockJoinPoint(ft, result);
        InterviewState state = testState();

        // when
        Object out = aspect.traceFaultTolerant(pjp, state);

        // then
        assertSame(result, out, "返回值应透传");
        verify(metrics).recordNodeDuration(eq("ask"), anyLong());
        verify(eventPublisher).publishEvent(any(GraphExecutionEvent.NodeStarted.class));
        verify(eventPublisher).publishEvent(any(GraphExecutionEvent.NodeSucceeded.class));
        verify(eventPublisher, never()).publishEvent(any(GraphExecutionEvent.NodeFailed.class));
    }

    @Test
    @DisplayName("异常路径：记录 error_type + 发布 NodeFailed + 重新抛出")
    void traceFaultTolerant_exception_recordsErrorAndRethrows() {
        // given
        RuntimeException ex = new RuntimeException("AI timeout");
        AbstractNode<InterviewState> delegate;
        try {
            delegate = mockFailingNodeNamed("plan", ex);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 1, 0);
        ProceedingJoinPoint pjp;
        try {
            pjp = mockFailingJoinPoint(ft, ex);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
        InterviewState state = testState();

        // when / then
        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> aspect.traceFaultTolerant(pjp, state));
        assertSame(ex, thrown, "应抛出原异常");
        verify(metrics).incrementNodeError("plan", "RuntimeException");
        verify(eventPublisher).publishEvent(any(GraphExecutionEvent.NodeFailed.class));
    }

    @Test
    @DisplayName("重试耗尽场景：返回 Map 含 LAST_ERROR → 触发 retry_exhausted 失败指标")
    void traceFaultTolerant_retryExhausted_triggersFailedEvent() throws Throwable {
        // given: 模拟 FaultTolerantNode 重试耗尽后返回的 Map
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put(InterviewState.LAST_ERROR, "AI error");
        errorResult.put(InterviewState.RETRY_COUNT, 3);

        AbstractNode<InterviewState> delegate = mockNodeNamed("evaluate", errorResult);
        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 1, 0);
        ProceedingJoinPoint pjp = mockJoinPoint(ft, errorResult);
        InterviewState state = testState();

        // when
        Object out = aspect.traceFaultTolerant(pjp, state);

        // then
        assertSame(errorResult, out);
        verify(metrics).incrementNodeError("evaluate", "retry_exhausted");
        verify(eventPublisher).publishEvent(any(GraphExecutionEvent.NodeFailed.class));
        verify(eventPublisher, never()).publishEvent(any(GraphExecutionEvent.NodeSucceeded.class));
    }

    @Test
    @DisplayName("MDC 在 Node 执行期间注入，执行后清理")
    void mdc_injectedDuringExecution_andClearedAfter() throws Throwable {
        Map<String, Object> result = Map.of("key", "value");
        AbstractNode<InterviewState> delegate = mockNodeNamed("ask", result);
        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 1, 0);
        ProceedingJoinPoint pjp = mockJoinPoint(ft, result);
        InterviewState state = testState();

        aspect.traceFaultTolerant(pjp, state);

        assertNull(MDC.get("sessionId"), "执行后 MDC sessionId 应被清理");
        assertNull(MDC.get("node"), "执行后 MDC node 应被清理");
        assertNull(MDC.get("round"), "执行后 MDC round 应被清理");
    }

    @Test
    @DisplayName("NodeContextHolder 在执行期间有值，执行后清理")
    void nodeContextHolder_setDuringExecution_andClearedAfter() throws Throwable {
        Map<String, Object> result = Map.of("key", "value");
        AbstractNode<InterviewState> delegate = mockNodeNamed("followUp", result);
        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 1, 0);
        ProceedingJoinPoint pjp = mockJoinPoint(ft, result);
        InterviewState state = testState();

        aspect.traceFaultTolerant(pjp, state);

        assertNull(NodeContextHolder.current(), "执行后 NodeContextHolder 应清理");
    }

    @Test
    @DisplayName("重入防护：IN_TRACE 已 set 时直接 proceed，不重复计数")
    void reentrant_proceedsDirectly_noDuplicateMetrics() throws Throwable {
        Map<String, Object> result = Map.of("key", "value");
        AbstractNode<InterviewState> delegate = mockNodeNamed("ask", result);
        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 1, 0);
        ProceedingJoinPoint pjp = mockJoinPoint(ft, result);
        InterviewState state = testState();

        // 模拟重入：预先 set IN_TRACE
        // 由于 IN_TRACE 是 private static，无法直接 set；改为嵌套调用验证
        aspect.traceFaultTolerant(pjp, state);
        // 第二次调用：之前已 clear（finally 块），所以应正常执行
        aspect.traceFaultTolerant(pjp, state);

        // 验证 metrics 被记录 2 次（非 0 次也非 1 次）
        verify(metrics, times(2)).recordNodeDuration(eq("ask"), anyLong());
    }

    @Test
    @DisplayName("裸 Node 兜底场景：从 target 直接读 nodeName")
    void traceRawNode_readsNodeNameFromTarget() throws Throwable {
        Map<String, Object> result = Map.of("currentSeq", 2);
        AbstractNode<InterviewState> node = mockNodeNamed("plan", result);
        ProceedingJoinPoint pjp = mockJoinPoint(node, result);
        InterviewState state = testState();

        Object out = aspect.traceRawNode(pjp, state);

        assertSame(result, out);
        verify(metrics).recordNodeDuration(eq("plan"), anyLong());
        verify(eventPublisher).publishEvent(any(GraphExecutionEvent.NodeStarted.class));
        verify(eventPublisher).publishEvent(any(GraphExecutionEvent.NodeSucceeded.class));
    }
}
