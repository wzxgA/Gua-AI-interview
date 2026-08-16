package com.aims.infra.persistence.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelRouter;
import com.aims.core.common.exception.BizException;
import com.aims.core.question.ParsedQuestion;
import com.aims.infra.persistence.dto.InterviewNoteParseTask;
import com.aims.infra.persistence.dto.QuestionParseResult;
import com.aims.infra.persistence.entity.QuestionEntity;
import com.aims.infra.persistence.mapper.QuestionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    @Mock private QuestionMapper baseMapper;
    @Mock private ModelRouter modelRouter;
    @Mock private AiChatFacade aiChatFacade;

    private QuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionServiceImpl(baseMapper, modelRouter, aiChatFacade);
    }

    @Test
    void parseInterviewNote_normalizesAndMarksDuplicate() {
        when(aiChatFacade.call(any(), any(), any()))
                .thenReturn(
                        """
{"questions": [
  {"category": "TECHNICAL", "topic": "Redis 缓存", "difficulty": "MEDIUM", "content": "Redis 缓存穿透怎么解决？", "standardAnswer": "布隆过滤器等", "tags": ["Redis", "缓存"]},
  {"category": "UNKNOWN", "topic": "", "difficulty": "HARD", "content": "  ", "standardAnswer": "x", "tags": null}
]}
""");

        QuestionEntity existing = new QuestionEntity();
        existing.setId(9L);
        existing.setContent("Redis 缓存穿透怎么解决？");
        when(baseMapper.selectList(any())).thenReturn(List.of(existing));

        List<QuestionParseResult> results = service.parseInterviewNote("面经", null);

        assertEquals(1, results.size());
        QuestionParseResult result = results.get(0);
        assertEquals("Redis 缓存穿透怎么解决？", result.parsed().content());
        assertEquals(9L, result.matchedExistingId());
    }

    @Test
    void parseInterviewNote_invalidEnumFallsBackToDefaults() {
        when(aiChatFacade.call(any(), any(), any()))
                .thenReturn(
                        """
{"questions": [{"category": "UNKNOWN", "topic": null, "difficulty": "IMPOSSIBLE", "content": "如何设计秒杀系统？", "standardAnswer": null, "tags": null}]}
""");
        when(baseMapper.selectList(any())).thenReturn(List.of());

        List<QuestionParseResult> results = service.parseInterviewNote("面经", "Java");

        assertEquals(1, results.size());
        ParsedQuestion q = results.get(0).parsed();
        assertEquals("TECHNICAL", q.category());
        assertEquals("MEDIUM", q.difficulty());
        assertEquals("如何设计秒杀系统？", q.topic());
        assertEquals(0, q.tags().size());
        assertNull(results.get(0).matchedExistingId());
    }

    @Test
    void parseInterviewNote_toleratesMarkdownCodeFence() {
        when(aiChatFacade.call(any(), any(), any()))
                .thenReturn(
                        """
```json
{"questions": [{"category": "TECHNICAL", "topic": "Java 并发", "difficulty": "MEDIUM", "content": "Synchronized 与 ReentrantLock 的区别？", "standardAnswer": "", "tags": ["Java", "并发"]}]}
```
""");
        when(baseMapper.selectList(any())).thenReturn(List.of());

        List<QuestionParseResult> results = service.parseInterviewNote("面经", null);

        assertEquals(1, results.size());
        assertEquals("Synchronized 与 ReentrantLock 的区别？", results.get(0).parsed().content());
    }

    @Test
    void parseInterviewNote_emptyQuestions_throws() {
        when(aiChatFacade.call(any(), any(), any())).thenReturn("{\"questions\": []}");

        assertThrows(BizException.class, () -> service.parseInterviewNote("只有自我介绍", null));
    }

    @Test
    void parseInterviewNote_invalidJson_throws() {
        when(aiChatFacade.call(any(), any(), any())).thenReturn("这不是 JSON，只是闲聊");

        assertThrows(BizException.class, () -> service.parseInterviewNote("面经", null));
    }

    @Test
    void parseInterviewNote_nullOutput_throws() {
        when(aiChatFacade.call(any(), any(), any())).thenReturn(null);

        assertThrows(BizException.class, () -> service.parseInterviewNote("面经", null));
    }

    @Test
    void parseInterviewNoteAsync_completesWithResults() throws Exception {
        when(aiChatFacade.call(any(), any(), any()))
                .thenReturn(
                        """
{"questions": [{"category": "TECHNICAL", "topic": "Java", "difficulty": "MEDIUM", "content": "什么是多态？", "standardAnswer": "", "tags": ["Java"]}]}
""");
        when(baseMapper.selectList(any())).thenReturn(List.of());

        String taskId = service.parseInterviewNoteAsync("面经", null);
        assertNotNull(taskId);

        // 虚拟线程异步执行，轮询等待终态
        InterviewNoteParseTask task = null;
        for (int i = 0; i < 100; i++) {
            InterviewNoteParseTask current = service.getNoteParseTask(taskId);
            if (current != null && !"RUNNING".equals(current.status())) {
                task = current;
                break;
            }
            Thread.sleep(10);
        }

        assertNotNull(task);
        assertEquals("SUCCESS", task.status());
        assertNotNull(task.results());
        assertEquals(1, task.results().size());
        assertEquals("什么是多态？", task.results().getFirst().parsed().content());
    }
}
