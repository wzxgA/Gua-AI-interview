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
        when(executor.crossCheck(1L, "answer", null)).thenReturn(result(0.85));

        ResumeCrossCheckResult r = tool.crossCheck(1L, "answer", null);

        assertTrue(r.likelyConsistent());
        assertTrue(r.score() >= 0.7);
        verify(executor).crossCheck(1L, "answer", null);
    }

    @Test
    void crossCheck_lowScore_likelyConflict() {
        when(executor.crossCheck(1L, "answer", null)).thenReturn(result(0.3));

        ResumeCrossCheckResult r = tool.crossCheck(1L, "answer", null);

        assertTrue(r.likelyConflict());
        assertEquals(0.3, r.score());
        assertEquals("命中片段", r.matchedSnippet());
    }

    @Test
    void crossCheck_executorNull_returnsNull_notThrow() {
        when(executor.crossCheck(1L, "answer", "字节")).thenReturn(null);

        assertNull(tool.crossCheck(1L, "answer", "字节"));
    }

    @Test
    void crossCheck_companyHint_passedThrough() {
        ResumeCrossCheckResult r = result(0.8);
        when(executor.crossCheck(1L, "answer", "字节")).thenReturn(r);

        ResumeCrossCheckResult actual = tool.crossCheck(1L, "answer", "字节");

        assertEquals(r, actual);
        verify(executor).crossCheck(1L, "answer", "字节");
    }

    @Test
    void crossCheck_withConflictDetails_likelyConflict() {
        ResumeCrossCheckResult r =
                new ResumeCrossCheckResult(
                        "张三",
                        0.9,
                        1.0,
                        null,
                        List.of(),
                        List.of("work"),
                        "ENTITY",
                        List.of(new ConflictDetail("company", null, "字节", "片段")));

        assertTrue(r.likelyConflict());
        assertTrue(!r.likelyConsistent());
        assertEquals(1, r.conflictDetails().size());
    }
}
