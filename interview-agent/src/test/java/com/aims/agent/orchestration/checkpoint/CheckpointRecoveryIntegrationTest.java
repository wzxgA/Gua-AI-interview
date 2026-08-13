package com.aims.agent.orchestration.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.session.SessionStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
 * Checkpoint 中断恢复集成测试。
 *
 * <p>验证 checkpoint 在 saver 实例间持久化，且恢复后的状态可驱动正确的路由决策 （{@code routeAfterEndCheck} 的判定条件：{@code
 * lastError} / {@code currentSeq >= totalRounds}）。 完整的图执行恢复由 Phase 5 Engine 改造覆盖；此处聚焦 checkpoint
 * 读写与状态还原能力。
 *
 * <p>使用 embedded-redis 在本机 6380 端口启动真实 Redis 实例，无需 Docker。
 *
 * @since 1.1.0
 */
class CheckpointRecoveryIntegrationTest {

    /** embedded-redis 固定端口：避开本机默认 6379，与 saver 集成测试类共用（Surefire 串行执行）。 */
    private static final int REDIS_PORT = 6380;

    private static final String THREAD_ID = "session-recover-1001";
    private static final String LATEST_KEY = "interview:checkpoint:" + THREAD_ID;

    private static RedisServer redisServer;
    private static StringRedisTemplate redisTemplate;

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
    void clean() {
        redisTemplate.delete(LATEST_KEY);
        redisTemplate.delete(LATEST_KEY + ":history");
    }

    private RunnableConfig config() {
        return RunnableConfig.builder().threadId(THREAD_ID).build();
    }

    private RedisCheckpointSaver newSaver() {
        return new RedisCheckpointSaver(
                redisTemplate, new CheckpointSerializer(), Duration.ofHours(24), true);
    }

    private Checkpoint saveAndReload(Map<String, Object> stateData, String nodeId)
            throws Exception {
        RedisCheckpointSaver writer = newSaver();
        Checkpoint cp =
                Checkpoint.builder()
                        .id("cp-1")
                        .nodeId(nodeId)
                        .nextNodeId("__end__")
                        .state(stateData)
                        .build();
        writer.put(config(), cp);

        // 模拟重启：用新实例加载
        Optional<Checkpoint> loaded = newSaver().get(config());
        assertTrue(loaded.isPresent());
        return loaded.get();
    }

    @Test
    @DisplayName("达上限中断→恢复：currentSeq>=totalRounds 应满足结束条件（路由到 END 触发评估）")
    void recover_afterEvaluate_continuesToEnd() throws Exception {
        Map<String, Object> state =
                Map.of(
                        InterviewState.SESSION_ID,
                        1001L,
                        InterviewState.SESSION_STATUS,
                        SessionStatus.EVALUATING,
                        InterviewState.CURRENT_SEQ,
                        8,
                        InterviewState.TOTAL_ROUNDS,
                        8);
        Checkpoint loaded = saveAndReload(state, "evaluate");

        InterviewState restored = new InterviewState(loaded.getState());
        // routeAfterEndCheck 判定：currentSeq >= totalRounds → END（FE.04：评估/报告由 Kafka 链路完成）
        assertTrue(restored.currentSeq() >= restored.totalRounds(), "应满足结束条件路由到 END");
    }

    @Test
    @DisplayName("plan 后中断→恢复：计划保留且 seq<totalRounds 可继续流程")
    void recover_afterPlan_continuesFullFlow() throws Exception {
        Map<String, Object> state =
                Map.of(
                        InterviewState.SESSION_ID,
                        1001L,
                        InterviewState.SESSION_STATUS,
                        SessionStatus.IN_PROGRESS,
                        InterviewState.INTERVIEW_PLAN,
                        samplePlan(),
                        InterviewState.CURRENT_SEQ,
                        0,
                        InterviewState.TOTAL_ROUNDS,
                        8);
        Checkpoint loaded = saveAndReload(state, "plan");

        InterviewState restored = new InterviewState(loaded.getState());
        assertNotNull(restored.interviewPlan());
        assertEquals("张三", restored.interviewPlan().candidateName());
        assertTrue(restored.currentSeq() < restored.totalRounds(), "应可继续提问循环");
    }

    @Test
    @DisplayName("无 checkpoint → 返回空，从 START 开始")
    void recover_noCheckpoint_startsFromScratch() throws Exception {
        Optional<Checkpoint> loaded = newSaver().get(config());

        assertTrue(loaded.isEmpty(), "无 checkpoint 时应返回空，图从 START 开始");
    }

    @Test
    @DisplayName("checkpoint 含 LAST_ERROR → 应满足错误终止条件（路由到 END）")
    void recover_withErrorInState_routesToEnd() throws Exception {
        Map<String, Object> state =
                Map.of(
                        InterviewState.SESSION_ID,
                        1001L,
                        InterviewState.SESSION_STATUS,
                        SessionStatus.IN_PROGRESS,
                        InterviewState.CURRENT_SEQ,
                        1,
                        InterviewState.TOTAL_ROUNDS,
                        8,
                        InterviewState.LAST_ERROR,
                        "LLM 调用超时");
        Checkpoint loaded = saveAndReload(state, "evaluate");

        InterviewState restored = new InterviewState(loaded.getState());
        // routeAfterEndCheck 判定：lastError 非空 → END（FE.04：评估已答轮次由 Kafka 链路完成）
        assertNotNull(restored.lastError(), "错误状态应被恢复并路由到 END");
    }

    private InterviewPlan samplePlan() {
        return new InterviewPlan(
                "张三",
                "Java 后端工程师",
                List.of(new PlanSection("基础", 8, "考察基础")),
                List.of(
                        new PlannedQuestion("q1", "IoC", "EASY", List.of("循环依赖"), "概念理解"),
                        new PlannedQuestion("q2", "AOP", "EASY", List.of("切面"), "概念理解"),
                        new PlannedQuestion("q3", "Bean", "MEDIUM", List.of("作用域"), "原理"),
                        new PlannedQuestion("q4", "事务", "MEDIUM", List.of("传播"), "原理"),
                        new PlannedQuestion("q5", "MVC", "MEDIUM", List.of("分发"), "原理"),
                        new PlannedQuestion("q6", "Boot", "HARD", List.of("自动装配"), "架构"),
                        new PlannedQuestion("q7", "Cloud", "HARD", List.of("注册"), "架构"),
                        new PlannedQuestion("q8", "JVM", "HARD", List.of("GC"), "底层")),
                60,
                "v1");
    }
}
