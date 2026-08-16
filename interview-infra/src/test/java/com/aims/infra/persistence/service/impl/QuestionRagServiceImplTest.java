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
import com.aims.infra.persistence.dto.QuestionFilter;
import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** 题库 RAG 混合检索单测：正常路径 / 结果缓存命中 / 关键词失败降级纯向量 / 缓存异常忽略。 */
class QuestionRagServiceImplTest {

    private ModelRouter modelRouter;
    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private QuestionRagServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        modelRouter = mock(ModelRouter.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        service = new QuestionRagServiceImpl(modelRouter, jdbcTemplate, redis, new ObjectMapper());
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

    private QuestionSearchResult q(
            long id, String category, String topic, String difficulty, String content) {
        double vector = 0.8;
        double keyword = 0.2;
        double score = vector * 0.7 + keyword * 0.3;
        return new QuestionSearchResult(
                id,
                category,
                topic,
                difficulty,
                content,
                null,
                score,
                vector,
                keyword,
                List.of(topic),
                List.of("topic"),
                topic,
                "HYBRID");
    }

    @Test
    void search_hybrid_returnsMetricsAndWritesCache() {
        when(modelRouter.embed("redis")).thenReturn(new float[] {1.0f, 2.0f, 3.0f});
        when(valueOps.get(anyString())).thenReturn(null);
        QuestionSearchResult q1 = q(1, "TECHNICAL", "缓存", "MEDIUM", "Redis 缓存穿透如何解决");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(q1));

        RagSearchResponse<QuestionSearchResult> resp = service.search("redis", 5);

        assertEquals(1, resp.results().size());
        assertEquals("HYBRID", resp.results().getFirst().recallSource());
        assertNotNull(resp.metrics());
        assertEquals(0.62, resp.results().getFirst().score(), 0.001);
        // 结果缓存与向量缓存均有写入
        verify(valueOps, org.mockito.Mockito.atLeastOnce())
                .set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void search_withFilter_appendsFilters() {
        when(modelRouter.embed("redis")).thenReturn(new float[] {1.0f, 2.0f, 3.0f});
        when(valueOps.get(anyString())).thenReturn(null);
        QuestionSearchResult q1 = q(1, "TECHNICAL", "缓存", "MEDIUM", "Redis 缓存穿透如何解决");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(q1));

        RagSearchResponse<QuestionSearchResult> resp =
                service.search("redis", new QuestionFilter("TECHNICAL", "MEDIUM"), 5);

        assertEquals(1, resp.results().size());
        // SQL 应包含过滤条件
        verify(jdbcTemplate)
                .query(
                        org.mockito.ArgumentMatchers.contains("AND category = ?"),
                        any(RowMapper.class),
                        any(Object[].class));
    }

    @Test
    void search_resultCacheHit_skipsEmbedAndSql() throws Exception {
        QuestionSearchResult q1 = q(1, "TECHNICAL", "缓存", "MEDIUM", "Redis 缓存穿透如何解决");
        String json = new ObjectMapper().writeValueAsString(List.of(q1));
        when(valueOps.get(anyString())).thenReturn(json);

        RagSearchResponse<QuestionSearchResult> resp = service.search("redis", 5);

        assertEquals(1, resp.results().size());
        verify(modelRouter, never()).embed(anyString());
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
        assertEquals(0, resp.metrics().embeddingMs());
    }

    @Test
    void search_keywordFailure_fallsBackToVector() {
        when(modelRouter.embed("redis")).thenReturn(new float[] {1.0f, 2.0f, 3.0f});
        when(valueOps.get(anyString())).thenReturn(null);
        QuestionSearchResult q2 =
                new QuestionSearchResult(
                        2L,
                        "BEHAVIORAL",
                        "团队协作",
                        "EASY",
                        "介绍一次团队协作经历",
                        null,
                        0.7,
                        0.7,
                        0.0,
                        List.of(),
                        List.of(),
                        null,
                        "VECTOR");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new RuntimeException("hybrid boom"))
                .thenReturn(List.of(q2));

        RagSearchResponse<QuestionSearchResult> resp = service.search("redis", 5);

        assertEquals("VECTOR", resp.results().getFirst().recallSource());
        assertEquals(0.0, resp.results().getFirst().keywordScore());
    }

    @Test
    void search_cacheErrors_areIgnored() {
        when(modelRouter.embed("redis")).thenReturn(new float[] {1.0f, 2.0f, 3.0f});
        // 结果缓存读抛异常 → 忽略走直查
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        QuestionSearchResult q1 = q(1, "TECHNICAL", "缓存", "MEDIUM", "Redis 缓存穿透如何解决");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(q1));

        RagSearchResponse<QuestionSearchResult> resp = service.search("redis", 5);

        assertEquals(1, resp.results().size());
        assertEquals("HYBRID", resp.results().getFirst().recallSource());
    }
}
