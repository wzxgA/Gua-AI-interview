package com.aims.agent.orchestration.checkpoint;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpType;
import com.aims.core.session.SessionStatus;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Map;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link RedisCheckpointSaver} 单元测试（Mock Redis）。
 *
 * @since 1.1.0
 */
class RedisCheckpointSaverTest {

    private static final String THREAD_ID = "session-1001";
    private static final String LATEST_KEY = "interview:checkpoint:" + THREAD_ID;
    private static final String HISTORY_KEY = LATEST_KEY + ":history";
    private static final Duration TTL = Duration.ofHours(24);

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;

    @SuppressWarnings("unchecked")
    private ListOperations<String, String> listOps;

    private CheckpointSerializer serializer;
    private RedisCheckpointSaver saver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        listOps = mock(ListOperations.class);
        serializer = new CheckpointSerializer();
        saver = new RedisCheckpointSaver(redis, serializer, TTL, true);
    }

    private RunnableConfig configWithThread() {
        return RunnableConfig.builder().threadId(THREAD_ID).build();
    }

    private Checkpoint sampleCheckpoint() {
        return Checkpoint.builder()
                .id("cp-1")
                .nodeId("evaluate")
                .nextNodeId("summary")
                .state(
                        Map.of(
                                InterviewState.SESSION_ID, 1001L,
                                InterviewState.SESSION_STATUS, SessionStatus.EVALUATING,
                                InterviewState.FOLLOW_UP_TYPE, FollowUpType.NONE))
                .build();
    }

    // ─── insertedCheckpoint ───

    @Test
    @DisplayName("insertedCheckpoint 写入 Redis（opsForValue.set 被调用）")
    void insertedCheckpoint_writesToRedis() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForList()).thenReturn(listOps);

        saver.insertedCheckpoint(configWithThread(), new LinkedList<>(), sampleCheckpoint());

        verify(valueOps).set(eq(LATEST_KEY), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("insertedCheckpoint 设置 24h TTL")
    void insertedCheckpoint_setsTtl() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForList()).thenReturn(listOps);

        saver.insertedCheckpoint(configWithThread(), new LinkedList<>(), sampleCheckpoint());

        ArgumentCaptor<Duration> captor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(LATEST_KEY), anyString(), captor.capture());
        assertEquals(Duration.ofHours(24), captor.getValue());
    }

    @Test
    @DisplayName("insertedCheckpoint 写入历史（opsForList.rightPush 被调用）")
    void insertedCheckpoint_writesHistory() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForList()).thenReturn(listOps);

        saver.insertedCheckpoint(configWithThread(), new LinkedList<>(), sampleCheckpoint());

        verify(listOps).rightPush(eq(HISTORY_KEY), anyString());
        verify(redis).expire(HISTORY_KEY, TTL);
    }

    @Test
    @DisplayName("historyEnabled=false 时 rightPush 未调用")
    void insertedCheckpoint_skipsHistory() throws Exception {
        saver = new RedisCheckpointSaver(redis, serializer, TTL, false);
        when(redis.opsForValue()).thenReturn(valueOps);

        saver.insertedCheckpoint(configWithThread(), new LinkedList<>(), sampleCheckpoint());

        verify(valueOps).set(eq(LATEST_KEY), anyString(), eq(TTL));
        verify(redis, never()).opsForList();
        verifyNoInteractions(listOps);
    }

    // ─── loadCheckpoints ───

    @Test
    @DisplayName("loadCheckpoints 无数据时返回空 LinkedList")
    void loadCheckpoints_returnsEmpty_whenNoData() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(LATEST_KEY)).thenReturn(null);

        LinkedList<Checkpoint> result = saver.loadCheckpoints(configWithThread());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("loadCheckpoints 返回含 1 个 Checkpoint")
    void loadCheckpoints_returnsCheckpoint() throws Exception {
        seedRedisWithCheckpoint();

        LinkedList<Checkpoint> result = saver.loadCheckpoints(configWithThread());

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("loadCheckpoints 字段正确：id/nodeId/nextNodeId/state")
    void loadCheckpoints_checkpointFieldsCorrect() throws Exception {
        seedRedisWithCheckpoint();

        LinkedList<Checkpoint> result = saver.loadCheckpoints(configWithThread());
        Checkpoint cp = result.peek();

        assertNotNull(cp);
        assertEquals("cp-1", cp.getId());
        assertEquals("evaluate", cp.getNodeId());
        assertEquals("summary", cp.getNextNodeId());
        assertEquals(1001L, cp.getState().get(InterviewState.SESSION_ID));
        assertEquals(SessionStatus.EVALUATING, cp.getState().get(InterviewState.SESSION_STATUS));
    }

    // ─── updatedCheckpoint ───

    @Test
    @DisplayName("updatedCheckpoint 覆盖同一 key（set 再次调用）")
    void updatedCheckpoint_overwritesExisting() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForList()).thenReturn(listOps);

        saver.insertedCheckpoint(configWithThread(), new LinkedList<>(), sampleCheckpoint());
        saver.updatedCheckpoint(configWithThread(), new LinkedList<>(), sampleCheckpoint());

        verify(valueOps, times(2)).set(eq(LATEST_KEY), anyString(), eq(TTL));
    }

    // ─── releaseCheckpoints ───

    @Test
    @DisplayName("releaseCheckpoints 删除 key + history key")
    void releaseCheckpoints_deletesKeys() throws Exception {
        saver.releaseCheckpoints(configWithThread(), new LinkedList<>());

        verify(redis).delete(LATEST_KEY);
        verify(redis).delete(HISTORY_KEY);
    }

    @Test
    @DisplayName("releaseCheckpoints 返回 Tag 含 threadId")
    void releaseCheckpoints_returnsTag() throws Exception {
        BaseCheckpointSaver.Tag tag =
                saver.releaseCheckpoints(configWithThread(), new LinkedList<>());

        assertNotNull(tag);
        assertEquals(THREAD_ID, tag.threadId());
    }

    // ─── 缺失 threadId ───

    @Test
    @DisplayName("loadCheckpoints 缺失 threadId 抛 IllegalStateException")
    void loadCheckpoints_missingThreadId_throws() {
        RunnableConfig emptyConfig = RunnableConfig.builder().build();
        assertThrows(IllegalStateException.class, () -> saver.loadCheckpoints(emptyConfig));
    }

    @Test
    @DisplayName("insertedCheckpoint 缺失 threadId 抛 IllegalStateException")
    void insertedCheckpoint_missingThreadId_throws() {
        RunnableConfig emptyConfig = RunnableConfig.builder().build();
        assertThrows(
                IllegalStateException.class,
                () ->
                        saver.insertedCheckpoint(
                                emptyConfig, new LinkedList<>(), sampleCheckpoint()));
    }

    // ─── 辅助 ───

    private void seedRedisWithCheckpoint() {
        CheckpointRecord record =
                new CheckpointRecord(
                        "cp-1",
                        "evaluate",
                        "summary",
                        Map.of(
                                InterviewState.SESSION_ID, 1001L,
                                InterviewState.SESSION_STATUS, SessionStatus.EVALUATING,
                                InterviewState.FOLLOW_UP_TYPE, FollowUpType.NONE),
                        System.currentTimeMillis());
        String json = serializer.serialize(record);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(LATEST_KEY)).thenReturn(json);
    }
}
