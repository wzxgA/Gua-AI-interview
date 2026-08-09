package com.aims.agent.orchestration.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpType;
import com.aims.core.session.SessionStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

/**
 * {@link RedisCheckpointSaver} embedded-redis 集成测试。
 *
 * <p>使用 embedded-redis 在本机启动一个真实 Redis 实例（默认端口 6380），验证 put/get 往返、 TTL、release、history 增长、跨实例恢复。无需
 * Docker，Windows 使用内置 Redis 5.0.14.1 二进制。
 *
 * @since 1.1.0
 */
class RedisCheckpointSaverIntegrationTest {

    /** embedded-redis 固定端口：避开本机默认 6379，与恢复测试类共用（Surefire 串行执行）。 */
    private static final int REDIS_PORT = 6380;

    private static final String THREAD_ID = "session-int-1001";
    private static final String LATEST_KEY = "interview:checkpoint:" + THREAD_ID;
    private static final String HISTORY_KEY = LATEST_KEY + ":history";

    private static RedisServer redisServer;
    private static StringRedisTemplate redisTemplate;

    private RedisCheckpointSaver saver;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = RedisServer.newRedisServer().port(REDIS_PORT).build();
        redisServer.start();

        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration("127.0.0.1", REDIS_PORT);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        redisTemplate.delete(LATEST_KEY);
        redisTemplate.delete(HISTORY_KEY);
        saver =
                new RedisCheckpointSaver(
                        redisTemplate, new CheckpointSerializer(), Duration.ofHours(24), true);
    }

    private RunnableConfig config() {
        return RunnableConfig.builder().threadId(THREAD_ID).build();
    }

    private Checkpoint checkpoint(String id, String nodeId, String next) {
        return Checkpoint.builder()
                .id(id)
                .nodeId(nodeId)
                .nextNodeId(next)
                .state(
                        Map.of(
                                InterviewState.SESSION_ID, 1001L,
                                InterviewState.SESSION_STATUS, SessionStatus.EVALUATING,
                                InterviewState.FOLLOW_UP_TYPE, FollowUpType.NONE))
                .build();
    }

    @Test
    @DisplayName("put→get 往返：state 数据与类型完整")
    void putThenGet_roundTrip() throws Exception {
        saver.put(config(), checkpoint("cp-1", "evaluate", "summary"));

        Optional<Checkpoint> loaded = saver.get(config());

        assertTrue(loaded.isPresent());
        Checkpoint cp = loaded.get();
        assertEquals("cp-1", cp.getId());
        assertEquals("evaluate", cp.getNodeId());
        assertEquals(1001L, cp.getState().get(InterviewState.SESSION_ID));
        assertEquals(SessionStatus.EVALUATING, cp.getState().get(InterviewState.SESSION_STATUS));
    }

    @Test
    @DisplayName("TTL 正确设置（key 存在过期时间）")
    void ttl_isSet() throws Exception {
        saver.put(config(), checkpoint("cp-1", "evaluate", "summary"));

        Long ttl = redisTemplate.getExpire(LATEST_KEY);
        assertNotNull(ttl);
        assertTrue(ttl > 0, "Redis key 应有 TTL");
    }

    @Test
    @DisplayName("release 删除 key 与 history key")
    void release_deletesKey() throws Exception {
        saver.put(config(), checkpoint("cp-1", "evaluate", "summary"));
        assertTrue(redisTemplate.hasKey(LATEST_KEY));

        saver.release(config());

        assertFalse(redisTemplate.hasKey(LATEST_KEY));
        assertFalse(redisTemplate.hasKey(HISTORY_KEY));
    }

    @Test
    @DisplayName("history List 随多次 put 增长")
    void history_grows() throws Exception {
        saver.put(config(), checkpoint("cp-1", "ask", "answer"));
        saver.put(config(), checkpoint("cp-2", "answer", "followUpDecision"));
        saver.put(config(), checkpoint("cp-3", "evaluate", "summary"));

        Long size = redisTemplate.opsForList().size(HISTORY_KEY);
        assertNotNull(size);
        assertEquals(3L, size);
    }

    @Test
    @DisplayName("跨实例恢复：新 saver 实例可加载旧实例写入的 checkpoint")
    void loadAfterRestart_simulation() throws Exception {
        saver.put(config(), checkpoint("cp-1", "evaluate", "summary"));

        // 模拟进程重启：用同一 Redis 构造新的 saver 实例
        RedisCheckpointSaver restarted =
                new RedisCheckpointSaver(
                        redisTemplate, new CheckpointSerializer(), Duration.ofHours(24), true);

        Optional<Checkpoint> loaded = restarted.get(config());

        assertTrue(loaded.isPresent());
        assertEquals("cp-1", loaded.get().getId());
        assertEquals(1001L, loaded.get().getState().get(InterviewState.SESSION_ID));
    }
}
