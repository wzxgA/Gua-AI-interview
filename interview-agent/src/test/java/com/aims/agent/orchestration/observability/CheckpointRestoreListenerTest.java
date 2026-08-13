package com.aims.agent.orchestration.observability;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link CheckpointRestoreListener} 单元测试：验证事件触发指标计数。
 *
 * @since 1.2.0 Phase 6
 */
@ExtendWith(MockitoExtension.class)
class CheckpointRestoreListenerTest {

    @Mock private GraphMetricsRegistry metrics;

    private CheckpointRestoreListener listener;

    @BeforeEach
    void setUp() {
        listener = new CheckpointRestoreListener(metrics);
    }

    @Test
    @DisplayName("checkpoint 来源：触发 incrementCheckpointRestore('checkpoint')")
    void onCheckpointRestored_checkpointSource_incrementsCounter() {
        listener.onCheckpointRestored(
                new GraphExecutionEvent.CheckpointRestored(
                        "session-42", "checkpoint", Instant.now()));

        verify(metrics).incrementCheckpointRestore("checkpoint");
    }

    @Test
    @DisplayName("db_rebuild 来源：触发 incrementCheckpointRestore('db_rebuild')")
    void onCheckpointRestored_dbRebuildSource_incrementsCounter() {
        listener.onCheckpointRestored(
                new GraphExecutionEvent.CheckpointRestored(
                        "session-42", "db_rebuild", Instant.now()));

        verify(metrics).incrementCheckpointRestore("db_rebuild");
    }
}
