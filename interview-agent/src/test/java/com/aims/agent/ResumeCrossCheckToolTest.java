package com.aims.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link ResumeCrossCheckTool} 单元测试：高匹配/低匹配/检索失败三路径。 */
class ResumeCrossCheckToolTest {

    private ResumeCrossCheckExecutor executor;
    private ResumeCrossCheckTool tool;

    @BeforeEach
    void setUp() {
        executor = mock(ResumeCrossCheckExecutor.class);
        tool = new ResumeCrossCheckTool(executor);
    }

    private ResumeCrossCheckResult result(double score) {
        return new ResumeCrossCheckResult(
                "张三", score, 0.5, "命中片段", List.of("Spring"), List.of("skills"), "HYBRID");
    }

    @Test
    void crossCheck_highScore_likelyConsistent() {
        when(executor.crossCheck(1L, "answer")).thenReturn(result(0.85));

        ResumeCrossCheckResult r = tool.crossCheck(1L, "answer");

        assertTrue(r.likelyConsistent());
        assertTrue(r.score() >= 0.7);
        verify(executor).crossCheck(1L, "answer");
    }

    @Test
    void crossCheck_lowScore_likelyConflict() {
        when(executor.crossCheck(1L, "answer")).thenReturn(result(0.3));

        ResumeCrossCheckResult r = tool.crossCheck(1L, "answer");

        assertTrue(r.likelyConflict());
        assertEquals(0.3, r.score());
        assertEquals("命中片段", r.matchedSnippet());
    }

    @Test
    void crossCheck_executorNull_returnsNull_notThrow() {
        when(executor.crossCheck(1L, "answer")).thenReturn(null);

        assertNull(tool.crossCheck(1L, "answer"));
    }
}
