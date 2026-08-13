package com.aims.agent.orchestration.checkpoint;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.aims.agent.orchestration.graph.InterviewGraphFactory;
import com.aims.agent.orchestration.node.AnswerNode;
import com.aims.agent.orchestration.node.EndCheckNode;
import com.aims.agent.orchestration.node.FollowUpDecisionNode;
import com.aims.agent.orchestration.node.FollowUpNode;
import com.aims.agent.orchestration.node.PlanNode;
import com.aims.agent.orchestration.node.QuestionNode;
import com.aims.agent.orchestration.node.SummaryNode;
import java.time.Duration;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 验证 {@link RedisCheckpointSaver} 能与 {@link InterviewGraphFactory#compile} 集成编译通过。
 *
 * <p>对应验收项 A5：{@code compile(redisCheckpointSaver)} 编译通过。
 *
 * @since 1.1.0
 */
class CheckpointGraphCompileTest {

    @Test
    void compile_withRedisCheckpointSaver_succeeds() throws Exception {
        InterviewGraphFactory factory =
                new InterviewGraphFactory(
                        mock(PlanNode.class),
                        mock(QuestionNode.class),
                        mock(AnswerNode.class),
                        mock(FollowUpDecisionNode.class),
                        mock(FollowUpNode.class),
                        mock(SummaryNode.class),
                        mock(EndCheckNode.class));
        RedisCheckpointSaver saver =
                new RedisCheckpointSaver(
                        mock(StringRedisTemplate.class),
                        new CheckpointSerializer(),
                        Duration.ofHours(24),
                        true);

        CompiledGraph<?> graph = factory.compile(saver);

        assertNotNull(graph, "带 RedisCheckpointSaver 的图应编译成功");
    }
}
