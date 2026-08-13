package com.aims.gateway.orchestration.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aims.gateway.orchestration.InterviewWorkflowEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * {@link GraphHealthIndicator} 单元测试。
 *
 * <p>验证 Graph 就绪 / 灰度关闭 / Engine 缺失三种场景下的健康状态。
 *
 * @since 1.2.0 Phase 6
 */
class GraphHealthIndicatorTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<InterviewWorkflowEngine> engineProvider() {
        return mock(ObjectProvider.class);
    }

    private Health health(ObjectProvider<InterviewWorkflowEngine> provider) {
        GraphHealthIndicator indicator = new GraphHealthIndicator(provider);
        Health.Builder builder = new Health.Builder();
        try {
            var method =
                    GraphHealthIndicator.class.getDeclaredMethod(
                            "doHealthCheck", Health.Builder.class);
            method.setAccessible(true);
            method.invoke(indicator, builder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return builder.build();
    }

    @Test
    @DisplayName("Engine 缺失：返回 DOWN")
    void engineMissing_returnsDown() {
        var provider = engineProvider();
        when(provider.getIfAvailable()).thenReturn(null);

        Health h = health(provider);

        assertEquals(Status.DOWN, h.getStatus());
        assertEquals("InterviewWorkflowEngine bean not present", h.getDetails().get("error"));
    }

    @Test
    @DisplayName("Graph 就绪 + checkpoint 可用：返回 UP")
    void graphReady_checkpointAvailable_returnsUp() {
        InterviewWorkflowEngine engine = mock(InterviewWorkflowEngine.class);
        when(engine.isGraphReady()).thenReturn(true);
        when(engine.isCheckpointBackendAvailable()).thenReturn(true);
        when(engine.isEnabled()).thenReturn(true);

        var provider = engineProvider();
        when(provider.getIfAvailable()).thenReturn(engine);

        Health h = health(provider);

        assertEquals(Status.UP, h.getStatus());
        assertEquals("ready", h.getDetails().get("compiled"));
        assertEquals("redis", h.getDetails().get("checkpointBackend"));
    }

    @Test
    @DisplayName("灰度关闭 + Graph 未就绪：返回 OUT_OF_SERVICE")
    void engineDisabled_graphNotReady_returnsOutOfService() {
        InterviewWorkflowEngine engine = mock(InterviewWorkflowEngine.class);
        when(engine.isGraphReady()).thenReturn(false);
        when(engine.isCheckpointBackendAvailable()).thenReturn(true);
        when(engine.isEnabled()).thenReturn(false);

        var provider = engineProvider();
        when(provider.getIfAvailable()).thenReturn(engine);

        Health h = health(provider);

        assertEquals(Status.OUT_OF_SERVICE, h.getStatus());
        assertNotNull(h.getDetails().get("reason"), "应包含 reason 详情");
        assertTrue(((String) h.getDetails().get("reason")).contains("disabled"));
    }
}
