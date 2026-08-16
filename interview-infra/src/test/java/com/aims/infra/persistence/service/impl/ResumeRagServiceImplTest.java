package com.aims.infra.persistence.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.ai.router.ModelRouter;
import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.ResumeSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** 简历 RAG 混合检索单测：正常路径 / 结果缓存命中 / 关键词失败降级纯向量 / 缓存异常忽略。 */
class ResumeRagServiceImplTest {

    private ModelRouter modelRouter;
    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ResumeRagServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        modelRouter = mock(ModelRouter.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        service = new ResumeRagServiceImpl(modelRouter, jdbcTemplate, redis, new ObjectMapper());
        enableCaches(true, true);
    }

    private void enableCaches(boolean result, boolean embedding) throws Exception {
        setField(service, "resultCacheEnabled", result);
        setField(service, "embeddingCacheEnabled", embedding);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ResumeSearchResult r(long id, String name, double keyword, String recallSource) {
        double vector = 0.8;
        double score = vector * 0.7 + keyword * 0.3;
        return new ResumeSearchResult(
                id,
                name,
                "13800000000",
                "a@b.com",
                "Java 工程师",
                5,
                List.of("Java", "Redis"),
                score,
                vector,
                keyword,
                "技能：Java、Redis",
                "text-embedding-v4",
                List.of("Java"),
                List.of("skills"),
                recallSource);
    }

    @Test
    void search_hybrid_returnsMetricsAndWritesCache() {
        when(modelRouter.embed("java")).thenReturn(new float[] {1.0f, 2.0f, 3.0f});
        when(valueOps.get(anyString())).thenReturn(null);
        ResumeSearchResult r1 = r(1, "张三", 0.2, "HYBRID");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(r1));

        RagSearchResponse<ResumeSearchResult> resp = service.search("java", 5, null);

        assertEquals(1, resp.results().size());
        assertEquals("HYBRID", resp.results().getFirst().recallSource());
        assertNotNull(resp.metrics());
        assertEquals(0.62, resp.results().getFirst().score(), 0.001);
        // 结果缓存与向量缓存均有写入
        verify(valueOps, org.mockito.Mockito.atLeastOnce())
                .set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void search_resultCacheHit_skipsEmbedAndSql() throws Exception {
        ResumeSearchResult r1 = r(1, "张三", 0.2, "HYBRID");
        String json = new ObjectMapper().writeValueAsString(List.of(r1));
        when(valueOps.get(anyString())).thenReturn(json);

        RagSearchResponse<ResumeSearchResult> resp = service.search("java", 5, null);

        assertEquals(1, resp.results().size());
        verify(modelRouter, never()).embed(anyString());
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
        assertEquals(0, resp.metrics().embeddingMs());
    }

    @Test
    void search_keywordFailure_fallsBackToVector() {
        when(modelRouter.embed("java")).thenReturn(new float[] {1.0f, 2.0f, 3.0f});
        when(valueOps.get(anyString())).thenReturn(null);
        ResumeSearchResult r2 = r(2, "李四", 0.0, "VECTOR");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new RuntimeException("hybrid boom"))
                .thenReturn(List.of(r2));

        RagSearchResponse<ResumeSearchResult> resp = service.search("java", 5, null);

        assertEquals("VECTOR", resp.results().getFirst().recallSource());
        assertEquals(0.0, resp.results().getFirst().keywordScore());
    }

    @Test
    void search_cacheErrors_areIgnored() {
        when(modelRouter.embed("java")).thenReturn(new float[] {1.0f, 2.0f, 3.0f});
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        ResumeSearchResult r1 = r(1, "张三", 0.2, "HYBRID");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(r1));

        RagSearchResponse<ResumeSearchResult> resp = service.search("java", 5, null);

        assertEquals(1, resp.results().size());
        assertEquals("HYBRID", resp.results().getFirst().recallSource());
    }
}
