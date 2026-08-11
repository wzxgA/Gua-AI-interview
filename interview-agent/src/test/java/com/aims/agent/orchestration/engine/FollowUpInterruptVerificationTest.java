package com.aims.agent.orchestration.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.aims.agent.FollowUpAgent;
import com.aims.agent.InterviewPlanGenerator;
import com.aims.agent.InterviewerAgent;
import com.aims.agent.SummaryAgent;
import com.aims.agent.orchestration.checkpoint.CheckpointSerializer;
import com.aims.agent.orchestration.checkpoint.RedisCheckpointSaver;
import com.aims.agent.orchestration.graph.InterviewGraphFactory;
import com.aims.agent.orchestration.node.AnswerNode;
import com.aims.agent.orchestration.node.EndCheckNode;
import com.aims.agent.orchestration.node.FollowUpDecisionNode;
import com.aims.agent.orchestration.node.FollowUpNode;
import com.aims.agent.orchestration.node.PlanNode;
import com.aims.agent.orchestration.node.QuestionNode;
import com.aims.agent.orchestration.node.StreamEmitter;
import com.aims.agent.orchestration.node.SummaryNode;
import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.PlanSection;
import com.aims.core.interview.PlannedQuestion;
import com.aims.core.interview.QaPair;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;
import redis.embedded.RedisServer;

/**
 * 追问闭环验证测试（LG.10 T8 API 验证 + 端到端）。
 *
 * <p>验证目标：
 *
 * <ol>
 *   <li>interruptBefore(ANSWER) 对 followUp → answer 入边同样生效（节点级中断）
 *   <li>追问生成后 Graph 暂停，提交追问回答可恢复并进入递归决策
 *   <li>追问回答以带 followUpIndex/followUpType 的 QaPair 进入 QA_HISTORY
 * </ol>
 *
 * <p>恢复语义：必须使用 {@code GraphInput.resume(updateMap)} 从 checkpoint 断点继续； {@code invoke(Map)} 是
 * GraphArgs 语义，会从 START 重跑 plan/ask 并丢失注入的回答（langgraph4j 1.8.22 源码证实）。
 *
 * <p>使用 embedded-redis（6380 端口，与 CheckpointRecoveryIntegrationTest 共用，Surefire 串行执行）。
 */
@ExtendWith(MockitoExtension.class)
class FollowUpInterruptVerificationTest {

    private static final int REDIS_PORT = 6380;
    private static final String THREAD_ID = "session-followup-2001";

    private static RedisServer redisServer;
    private static StringRedisTemplate redisTemplate;

    @Mock private InterviewPlanGenerator planGenerator;
    @Mock private InterviewerAgent interviewerAgent;
    @Mock private FollowUpAgent followUpAgent;
    @Mock private SummaryAgent summaryAgent;

    private InterviewGraphFactory factory;
    private RedisCheckpointSaver saver;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = RedisServer.newRedisServer().port(REDIS_PORT).build();
        redisServer.start();

        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration("127.0.0.1", REDIS_PORT);
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
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
        redisTemplate.delete("interview:checkpoint:" + THREAD_ID);
        redisTemplate.delete("interview:checkpoint:" + THREAD_ID + ":history");

        StreamEmitter emitter = StreamEmitter.NOOP;
        factory =
                new InterviewGraphFactory(
                        new PlanNode(planGenerator),
                        new QuestionNode(interviewerAgent, emitter),
                        new AnswerNode(),
                        new FollowUpDecisionNode(followUpAgent),
                        new FollowUpNode(followUpAgent, emitter),
                        null, // evaluateNode 已移至 Kafka 链路（FE.04），图内不注册，保留占位
                        new SummaryNode(summaryAgent),
                        new EndCheckNode(),
                        null); // reportNode 已移至 Kafka 链路（FE.04），图内不注册，保留占位
        saver =
                new RedisCheckpointSaver(
                        redisTemplate, new CheckpointSerializer(), Duration.ofHours(24), true);
    }

    private RunnableConfig config() {
        return RunnableConfig.builder().threadId(THREAD_ID).build();
    }

    private InterviewPlan mockPlan() {
        List<PlannedQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            questions.add(
                    new PlannedQuestion(
                            "q" + i, "topic" + i, "BALANCED", List.of("hint" + i), "focus" + i));
        }
        return new InterviewPlan(
                "候选人", "Java", List.of(new PlanSection("技术", 8, "技术评估")), questions, 45, "1.0");
    }

    private Map<String, Object> baseInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(InterviewState.SESSION_ID, 2001L);
        inputs.put(InterviewState.CANDIDATE_NAME, "候选人");
        inputs.put(InterviewState.POSITION_TITLE, "Java");
        inputs.put(InterviewState.JD_TEXT, "JD");
        inputs.put(InterviewState.RESUME_SUMMARY, "简历");
        inputs.put(InterviewState.PERSONA, InterviewerPersona.FRIENDLY);
        inputs.put(InterviewState.TOTAL_ROUNDS, 3);
        return inputs;
    }

    @Test
    @DisplayName("追问闭环：主问题暂停→回答→追问生成后再次暂停→追问回答恢复→进入下一题")
    void followUp_pauses_andResumes() throws Exception {
        // Mock 行为
        when(planGenerator.generate(any(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(mockPlan());
        when(interviewerAgent.streamQuestion(any()))
                .thenReturn(Flux.just("主问题"))
                .thenReturn(Flux.just("下一题"));
        // 决策：主问题回答后追问 1 次，追问回答后放行
        when(followUpAgent.evaluate(any()))
                .thenReturn(FollowUpDecision.of(FollowUpType.DEEPEN, "追问理由", "深挖"))
                .thenReturn(FollowUpDecision.noFollowUp("充分"));
        when(followUpAgent.streamFollowUp(any(), any())).thenReturn(Flux.just("追问问题"));
        // evaluatorAgent/reportAgent 不打桩：评估/报告已移至 Kafka 链路（FE.04），图内不再调用
        // summaryAgent.summarize 不打桩：QA 不足 5 条时 SummaryNode 跳过（Mockito 严格模式禁止无用桩）

        CompiledGraph<InterviewState> graph = factory.compileWithInterruptBeforeAnswer(saver);
        RunnableConfig config = config();

        // 1. 启动：plan → ask(Q1) → 暂停于 ANSWER 前
        graph.invoke(baseInputs(), config);

        // 2. 提交主问题回答：GraphInput.resume 从 checkpoint 断点恢复（invoke(Map) 会从 START 重跑，不可用）
        //    answer → decision(追问) → followUp → 暂停于 ANSWER 前（followUp→answer 入边）
        Optional<InterviewState> afterFollowUp =
                graph.invoke(
                        GraphInput.resume(Map.of(InterviewState.CURRENT_ANSWER, "主回答")), config);
        assertTrue(afterFollowUp.isPresent(), "追问生成后应返回暂停 state");
        InterviewState pausedAtFollowUp = afterFollowUp.get();
        assertEquals("追问问题", pausedAtFollowUp.currentQuestion(), "暂停时当前问题应为追问");
        assertTrue(pausedAtFollowUp.pendingFollowUp(), "PENDING_FOLLOW_UP 应为 true");
        assertEquals(1, pausedAtFollowUp.followUpIndex(), "followUpIndex 应为 1");
        assertEquals(1, pausedAtFollowUp.followUpCount(), "本题追问计数应为 1");
        assertEquals(1, pausedAtFollowUp.qaHistory().size(), "仅主问题 Q&A 已入库");

        // 3. 提交追问回答：answer(追问 QaPair) → decision(放行) → summary → endCheck → ask(Q2) → 暂停
        Optional<InterviewState> afterFollowUpAnswer =
                graph.invoke(
                        GraphInput.resume(Map.of(InterviewState.CURRENT_ANSWER, "追问回答")), config);
        assertTrue(afterFollowUpAnswer.isPresent(), "下一题生成后应返回暂停 state");
        InterviewState pausedAtNextQuestion = afterFollowUpAnswer.get();
        assertEquals(2, pausedAtNextQuestion.currentSeq(), "应进入第 2 题");
        assertEquals("下一题", pausedAtNextQuestion.currentQuestion());
        assertFalse(pausedAtNextQuestion.pendingFollowUp(), "换题后 PENDING_FOLLOW_UP 应重置");
        assertEquals(0, pausedAtNextQuestion.followUpCount(), "换题后追问计数应清零");

        // 4. QA_HISTORY：主 Q&A + 追问 Q&A（带标记）
        assertEquals(2, pausedAtNextQuestion.qaHistory().size());
        QaPair mainQa = pausedAtNextQuestion.qaHistory().get(0);
        QaPair followUpQa = pausedAtNextQuestion.qaHistory().get(1);
        assertFalse(mainQa.isFollowUp());
        assertEquals("主回答", mainQa.answer());
        assertTrue(followUpQa.isFollowUp(), "追问 Q&A 应带标记");
        assertEquals("追问问题", followUpQa.question());
        assertEquals("追问回答", followUpQa.answer());
        assertEquals(1, followUpQa.followUpIndex());
        assertEquals(FollowUpType.DEEPEN, followUpQa.followUpType());
        assertEquals(1, followUpQa.seq(), "追问 Q&A 的 seq 应沿用主问题 seq");
    }
}
