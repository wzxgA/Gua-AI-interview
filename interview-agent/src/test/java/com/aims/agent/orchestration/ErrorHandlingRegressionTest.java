package com.aims.agent.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aims.agent.orchestration.node.AbstractNode;
import com.aims.agent.orchestration.node.FaultTolerantNode;
import com.aims.agent.orchestration.observability.GraphMetricsRegistry;
import com.aims.agent.orchestration.observability.GraphTraceAspect;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.agent.orchestration.state.TestStateBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 错误处理专项回归测试（LG.8 T9）。
 *
 * <p>5 场景验证 Graph 编排在 Agent 异常时的容错行为与指标埋点，确保：
 *
 * <ol>
 *   <li>PlanNode 重试耗尽 → LAST_ERROR 写入 + retry_exhausted 指标（经 aspect）
 *   <li>QuestionNode 流式中断 → 重试后兜底成功（直接调 FaultTolerantNode）
 *   <li>EvaluateNode 解析失败 → 重试 1 次后成功（直接调 FaultTolerantNode）
 *   <li>ReportNode 超时 → aims.graph.node.error{node=report} +1（经 aspect）
 *   <li>FaultTolerantNode 重试耗尽 → State 含 LAST_ERROR + RETRY_COUNT（直接调）
 * </ol>
 *
 * <p>纯 Mock 测试，不依赖 Spring 容器/Docker/Redis。
 *
 * @since 1.3.0 Phase 7
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ErrorHandlingRegressionTest {

    private SimpleMeterRegistry meterRegistry;
    private GraphMetricsRegistry metrics;
    private GraphTraceAspect aspect;

    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProceedingJoinPoint pjp;

    @BeforeEach
    void setUp() throws Exception {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new GraphMetricsRegistry(meterRegistry);
        var initMethod = GraphMetricsRegistry.class.getDeclaredMethod("initGauges");
        initMethod.setAccessible(true);
        initMethod.invoke(metrics);
        aspect = new GraphTraceAspect(metrics, eventPublisher);
    }

    @Test
    @DisplayName("场景1: PlanNode 重试耗尽 → LAST_ERROR + retry_exhausted 指标触发（经 aspect）")
    void planNode_allRetriesFail_routesToReport() throws Throwable {
        // given: 模拟 FaultTolerantNode 重试耗尽后返回的 Map
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put(InterviewState.LAST_ERROR, "Plan generation failed");
        errorResult.put(InterviewState.RETRY_COUNT, 3);

        AbstractNode<InterviewState> delegate = mockNodeNamed("plan", errorResult);
        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 3, 0, metrics);
        when(pjp.getTarget()).thenReturn(ft);
        when(pjp.proceed()).thenReturn(errorResult);

        InterviewState state = TestStateBuilder.forTesting().withSessionId(1L).build();

        // when
        Map<String, Object> result = (Map<String, Object>) aspect.traceFaultTolerant(pjp, state);

        // then: LAST_ERROR 写入 State
        assertThat(result.get(InterviewState.LAST_ERROR)).isEqualTo("Plan generation failed");
        assertThat(result.get(InterviewState.RETRY_COUNT)).isEqualTo(3);

        // then: retry_exhausted 错误指标 +1
        assertThat(
                        meterRegistry
                                .find("aims.graph.node.error")
                                .tag("node", "plan")
                                .tag("error_type", "retry_exhausted")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("场景2: QuestionNode 流式中断 → 重试后兜底成功（retry 指标=1）")
    void questionNode_streamError_fallbackUsed() throws Exception {
        // given: 第一次失败，第二次成功（兜底）
        Map<String, Object> fallback = Map.of("currentQuestion", "兜底问题");
        AbstractNode<InterviewState> delegate = mock(AbstractNode.class);
        when(delegate.nodeName()).thenReturn("ask");
        when(delegate.apply(any()))
                .thenThrow(new RuntimeException("Stream interrupted"))
                .thenReturn(fallback);

        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 2, 0, metrics);
        InterviewState state = TestStateBuilder.forTesting().withSessionId(1L).build();

        // when: 直接调用 FaultTolerantNode（aspect 不负责重试，FTN 内部重试）
        Map<String, Object> result = ft.apply(state);

        // then: 兜底成功
        assertThat(result.get("currentQuestion")).isEqualTo("兜底问题");
        // then: 重试 1 次指标（attempt=1 失败后 increment，attempt=2 成功不 increment）
        assertThat(meterRegistry.find("aims.graph.node.retry").tag("node", "ask").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("场景3: EvaluateNode 解析失败 → 重试 1 次后成功（retry 指标=1）")
    void evaluateNode_parseException_retriesOnce() throws Exception {
        // given
        Map<String, Object> success = Map.of(InterviewState.ROUND_EVALUATIONS, "evaluated");
        AbstractNode<InterviewState> delegate = mock(AbstractNode.class);
        when(delegate.nodeName()).thenReturn("evaluate");
        when(delegate.apply(any()))
                .thenThrow(new RuntimeException("Parse exception"))
                .thenReturn(success);

        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 2, 0, metrics);
        InterviewState state = TestStateBuilder.forTesting().withSessionId(1L).build();

        // when
        Map<String, Object> result = ft.apply(state);

        // then: 重试后成功
        assertThat(result.get(InterviewState.ROUND_EVALUATIONS)).isEqualTo("evaluated");
        // then: 重试 1 次
        assertThat(
                        meterRegistry
                                .find("aims.graph.node.retry")
                                .tag("node", "evaluate")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("场景4: ReportNode 超时 → aims.graph.node.error{node=report} +1（经 aspect）")
    void reportNode_timeout_errorMetricIncremented() throws Throwable {
        // given: ReportNode 始终超时
        RuntimeException timeout = new RuntimeException("Report timeout");
        AbstractNode<InterviewState> delegate = mock(AbstractNode.class);
        when(delegate.nodeName()).thenReturn("report");
        when(delegate.apply(any())).thenThrow(timeout);

        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 2, 0, metrics);

        when(pjp.getTarget()).thenReturn(ft);
        when(pjp.proceed()).thenThrow(timeout);

        InterviewState state = TestStateBuilder.forTesting().withSessionId(1L).build();

        // when/then: aspect 抛出异常
        assertThrows(RuntimeException.class, () -> aspect.traceFaultTolerant(pjp, state));

        // then: 错误指标 +1
        assertThat(
                        meterRegistry
                                .find("aims.graph.node.error")
                                .tag("node", "report")
                                .tag("error_type", "RuntimeException")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("场景5: FaultTolerantNode 重试耗尽 → State 含 LAST_ERROR + RETRY_COUNT=3")
    void faultTolerantNode_retryExhausted_writesLastError() throws Exception {
        // given: delegate 始终失败
        AbstractNode<InterviewState> delegate = mock(AbstractNode.class);
        when(delegate.nodeName()).thenReturn("summary");
        when(delegate.apply(any())).thenThrow(new RuntimeException("Persistent failure"));

        FaultTolerantNode<InterviewState> ft = new FaultTolerantNode<>(delegate, 3, 0, metrics);
        InterviewState state = TestStateBuilder.forTesting().withSessionId(1L).build();

        // when: 直接调用 FaultTolerantNode
        Map<String, Object> result = ft.apply(state);

        // then: State 含 LAST_ERROR + RETRY_COUNT=3
        assertThat(result.get(InterviewState.LAST_ERROR)).isEqualTo("Persistent failure");
        assertThat(result.get(InterviewState.RETRY_COUNT)).isEqualTo(3);

        // then: 重试指标 = maxRetries-1 = 2（仅在 attempt < maxRetries 时 increment）
        assertThat(
                        meterRegistry
                                .find("aims.graph.node.retry")
                                .tag("node", "summary")
                                .counter()
                                .count())
                .isEqualTo(2.0);
    }

    @SuppressWarnings("unchecked")
    private AbstractNode<InterviewState> mockNodeNamed(String name, Map<String, Object> result)
            throws Exception {
        AbstractNode<InterviewState> node = mock(AbstractNode.class);
        when(node.nodeName()).thenReturn(name);
        when(node.apply(any())).thenReturn(result);
        return node;
    }
}
