package com.aims.infra.persistence.service.impl;

import com.aims.ai.router.ModelRouter;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.dto.QuestionFilter;
import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.aims.infra.persistence.service.QuestionRagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * 题库 RAG 检索实现：向量 + 关键词混合检索（对齐简历侧 0.7/0.3 权重）。
 *
 * <p>关键词检索使用 pg_trgm ILIKE（content=1.0 / topic=0.9 / category=0.8），结果带可解释性字段 （matchedTerms /
 * matchedFields / highlightSnippet / recallSource）。关键词检索异常时退化为纯向量检索。 检索结果与查询向量均走 Redis
 * 缓存（均可配置关闭），Redis 异常自动降级为直查。
 */
@Service
public class QuestionRagServiceImpl implements QuestionRagService {

    private static final Logger log = LoggerFactory.getLogger(QuestionRagServiceImpl.class);

    /** 向量得分权重。 */
    private static final double VECTOR_WEIGHT = 0.7;

    /** 关键词得分权重。 */
    private static final double KEYWORD_WEIGHT = 0.3;

    /** 结果缓存 TTL（秒）。 */
    private static final long RESULT_CACHE_TTL_SECONDS = 60;

    /** 查询向量缓存 TTL（秒）：近似 JD 更新时间粒度，避免失效挂钩复杂度。 */
    private static final long EMBED_CACHE_TTL_SECONDS = 30 * 60;

    private static final String RESULT_CACHE_PREFIX = "rag:q:";
    private static final String EMBED_CACHE_PREFIX = "rag:embed:";

    /** 命中字段标签（与 SQL CASE 分级一致）。 */
    private static final String[] FIELD_LABELS = {"content", "topic", "category"};

    private static final TypeReference<List<QuestionSearchResult>> RESULT_LIST_TYPE =
            new TypeReference<>() {};

    private final ModelRouter modelRouter;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rag.cache.result-enabled:true}")
    private boolean resultCacheEnabled;

    @Value("${rag.cache.embedding-enabled:true}")
    private boolean embeddingCacheEnabled;

    public QuestionRagServiceImpl(
            ModelRouter modelRouter,
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.modelRouter = modelRouter;
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagSearchResponse<QuestionSearchResult> search(String query, int topK) {
        return search(query, null, topK);
    }

    @Override
    public RagSearchResponse<QuestionSearchResult> search(
            String query, QuestionFilter filter, int topK) {
        long totalStart = System.nanoTime();

        // 1) 结果缓存
        String resultKey = resultCacheKey(query, filter, topK);
        List<QuestionSearchResult> cached = readResultCache(resultKey);
        if (cached != null) {
            long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.info(
                    "题库 RAG 命中结果缓存 query={} topK={} resultCount={} totalMs={}",
                    truncate(query),
                    topK,
                    cached.size(),
                    totalMs);
            return new RagSearchResponse<>(
                    cached, new RagSearchResponse.SearchMetrics(0, 0, totalMs, cached.size()));
        }

        // 2) 查询向量（带缓存）
        long embedStart = System.nanoTime();
        String vectorStr = embedCached(query);
        long embedMs = (System.nanoTime() - embedStart) / 1_000_000;

        // 3) 混合检索（失败降级纯向量）
        long sqlStart = System.nanoTime();
        List<QuestionSearchResult> results;
        String recallSource;
        try {
            results = hybridSearch(query, vectorStr, filter, topK);
            recallSource = "HYBRID";
        } catch (Exception e) {
            log.warn("题库混合检索失败，退化为纯向量检索 query={}", truncate(query), e);
            try {
                results = vectorOnlySearch(vectorStr, filter, topK);
                recallSource = "VECTOR";
            } catch (Exception ex) {
                long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
                log.error(
                        "题库 RAG 检索失败 query={} topK={} embeddingMs={} totalMs={}",
                        query,
                        topK,
                        embedMs,
                        totalMs,
                        ex);
                throw new BizException(ErrorCode.RAG_SEARCH_FAILED, "题库 RAG 检索失败", ex);
            }
        }
        long sqlMs = (System.nanoTime() - sqlStart) / 1_000_000;

        // 4) Java 侧按混合得分排序 + 截断 topK（复制列表，兼容不可变返回）
        List<QuestionSearchResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingDouble(QuestionSearchResult::score).reversed());
        if (sorted.size() > topK) {
            sorted = new ArrayList<>(sorted.subList(0, topK));
        }
        results = sorted;

        // 5) 写结果缓存
        writeResultCache(resultKey, results);

        long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
        log.info(
                "题库 RAG 混合检索 query={} topK={} recallSource={} embeddingMs={} sqlMs={}"
                        + " totalMs={} resultCount={}",
                truncate(query),
                topK,
                recallSource,
                embedMs,
                sqlMs,
                totalMs,
                results.size());
        return new RagSearchResponse<>(
                results,
                new RagSearchResponse.SearchMetrics(embedMs, sqlMs, totalMs, results.size()));
    }

    /** 混合检索：SQL 同时计算向量得分与关键词得分。 */
    private List<QuestionSearchResult> hybridSearch(
            String query, String vectorStr, QuestionFilter filter, int topK) {
        List<String> terms = tokenizeTerms(query);
        String keyword = sanitizeKeyword(query);

        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> params = new ArrayList<>();
        // SELECT 中的 ?::halfvec（向量得分）
        params.add(vectorStr);
        // CASE WHEN 中的关键词参数（3 个 ILIKE 占位符）
        params.add(keyword);
        params.add(keyword);
        params.add(keyword);

        appendFilter(sql, params, filter);

        // ORDER BY 按混合得分（PostgreSQL 内计算），LIMIT 取 3 倍 topK 供 Java 侧重排
        sql.append(" ORDER BY (1 - (embedding <=> ?::halfvec)) * ")
                .append(VECTOR_WEIGHT)
                .append(" + CASE WHEN content ILIKE '%' || ? || '%' THEN 1.0")
                .append(" WHEN topic ILIKE '%' || ? || '%' THEN 0.9")
                .append(" WHEN category ILIKE '%' || ? || '%' THEN 0.8 ELSE 0.0 END * ")
                .append(KEYWORD_WEIGHT)
                .append(" DESC LIMIT ?");
        params.add(vectorStr);
        params.add(keyword);
        params.add(keyword);
        params.add(keyword);
        params.add(topK * 3);

        RowMapper<QuestionSearchResult> mapper = createRowMapper(terms, "HYBRID");
        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    /** 纯向量检索 fallback。 */
    private List<QuestionSearchResult> vectorOnlySearch(
            String vectorStr, QuestionFilter filter, int topK) {
        StringBuilder sql =
                new StringBuilder(
                        "SELECT id, category, topic, difficulty, content, standard_answer, "
                                + "1 - (embedding <=> ?::halfvec) AS vector_score, "
                                + "0.0 AS keyword_score "
                                + "FROM question_bank WHERE embedding IS NOT NULL");
        List<Object> params = new ArrayList<>();
        params.add(vectorStr);

        appendFilter(sql, params, filter);

        sql.append(" ORDER BY embedding <=> ?::halfvec LIMIT ?");
        params.add(vectorStr);
        params.add(topK);

        RowMapper<QuestionSearchResult> mapper = createRowMapper(List.of(), "VECTOR");
        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    private static final String BASE_SQL =
            "SELECT id, category, topic, difficulty, content, standard_answer, "
                    + "1 - (embedding <=> ?::halfvec) AS vector_score, "
                    + "CASE WHEN content ILIKE '%' || ? || '%' THEN 1.0 "
                    + "WHEN topic ILIKE '%' || ? || '%' THEN 0.9 "
                    + "WHEN category ILIKE '%' || ? || '%' THEN 0.8 ELSE 0.0 END AS keyword_score "
                    + "FROM question_bank WHERE embedding IS NOT NULL";

    private void appendFilter(StringBuilder sql, List<Object> params, QuestionFilter filter) {
        if (filter != null) {
            if (filter.category() != null && !filter.category().isBlank()) {
                sql.append(" AND category = ?");
                params.add(filter.category());
            }
            if (filter.difficulty() != null && !filter.difficulty().isBlank()) {
                sql.append(" AND difficulty = ?");
                params.add(filter.difficulty());
            }
        }
    }

    private RowMapper<QuestionSearchResult> createRowMapper(
            List<String> terms, String recallSource) {
        return (rs, rowNum) -> {
            double vectorScore = rs.getDouble("vector_score");
            double keywordScore = rs.getDouble("keyword_score");
            double finalScore = vectorScore * VECTOR_WEIGHT + keywordScore * KEYWORD_WEIGHT;
            String content = rs.getString("content");
            String topic = rs.getString("topic");
            String category = rs.getString("category");

            List<String> matchedFields = new ArrayList<>();
            List<String> matchedTerms =
                    terms.stream()
                            .filter(t -> matchesAnyField(content, topic, category, t))
                            .toList();
            String highlight = buildHighlight(content, topic, category, terms, matchedFields);

            return new QuestionSearchResult(
                    rs.getLong("id"),
                    category,
                    topic,
                    rs.getString("difficulty"),
                    content,
                    rs.getString("standard_answer"),
                    finalScore,
                    vectorScore,
                    keywordScore,
                    matchedTerms,
                    matchedFields,
                    highlight,
                    recallSource);
        };
    }

    /** 判定 term 是否命中任一检索字段（content / topic / category）。 */
    private boolean matchesAnyField(String content, String topic, String category, String term) {
        if (term == null || term.length() < 2) return false;
        return containsIgnoreCase(content, term)
                || containsIgnoreCase(topic, term)
                || containsIgnoreCase(category, term);
    }

    private boolean containsIgnoreCase(String text, String term) {
        return text != null && text.toLowerCase().contains(term);
    }

    /** 从命中的字段中截取高亮片段（首个命中词附近 ±30 字符），并记录命中字段。 */
    private String buildHighlight(
            String content,
            String topic,
            String category,
            List<String> terms,
            List<String> matchedFields) {
        String[] fields = {content, topic, category};
        for (int i = 0; i < fields.length; i++) {
            String text = fields[i];
            if (text == null || text.isBlank()) continue;
            String lower = text.toLowerCase();
            for (String term : terms) {
                if (term == null || term.length() < 2) continue;
                int idx = lower.indexOf(term);
                if (idx >= 0) {
                    matchedFields.add(FIELD_LABELS[i]);
                    return snippetAround(text, idx);
                }
            }
        }
        return null;
    }

    /** 截取片段：命中位置 ±30 字符。 */
    private String snippetAround(String text, int start) {
        int from = Math.max(0, start - 30);
        int to = Math.min(text.length(), start + 30);
        String prefix = from > 0 ? "…" : "";
        String suffix = to < text.length() ? "…" : "";
        return prefix + text.substring(from, to) + suffix;
    }

    /**
     * 查询分词：按空白/标点切分出的 token（长度 ≥2）+ 整句兜底。
     *
     * <p>用于可解释性字段的 Java 侧判定；中文无分隔时以整句 token 兜底（命中概率低，向量检索主导）。
     */
    private List<String> tokenizeTerms(String query) {
        if (query == null || query.isBlank()) return List.of();
        Set<String> terms = new LinkedHashSet<>();
        String lower = query.toLowerCase();
        for (String tok : lower.split("[\\s\\p{Punct}，。、；：！？（）【】\"'《》]+")) {
            if (tok.length() >= 2) {
                terms.add(tok);
            }
        }
        String whole = lower.trim();
        if (whole.length() >= 2) {
            terms.add(whole);
        }
        return List.copyOf(terms);
    }

    /** 清理关键词，防止 SQL 注入（ILIKE 的 % 和 _ 转义）。 */
    private String sanitizeKeyword(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return query.replace("%", "\\%").replace("_", "\\_");
    }

    // ---- Redis 缓存（均可配置关闭，异常自动降级为直查） ----

    private List<QuestionSearchResult> readResultCache(String key) {
        if (!resultCacheEnabled || key == null) return null;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, RESULT_LIST_TYPE);
        } catch (Exception e) {
            log.debug("题库 RAG 结果缓存读取失败，忽略 key={}", key, e);
            return null;
        }
    }

    private void writeResultCache(String key, List<QuestionSearchResult> results) {
        if (!resultCacheEnabled || key == null || results == null || results.isEmpty()) return;
        try {
            stringRedisTemplate
                    .opsForValue()
                    .set(
                            key,
                            objectMapper.writeValueAsString(results),
                            Duration.ofSeconds(RESULT_CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.debug("题库 RAG 结果缓存写入失败，忽略 key={}", key, e);
        }
    }

    /** 查询向量：优先命中缓存，miss 时调用模型并回填。 */
    private String embedCached(String query) {
        String key = embeddingCacheEnabled ? cacheKey(EMBED_CACHE_PREFIX, query) : null;
        if (key != null) {
            try {
                String cached = stringRedisTemplate.opsForValue().get(key);
                if (cached != null && !cached.isBlank()) {
                    return cached;
                }
            } catch (Exception e) {
                log.debug("题库 RAG 向量缓存读取失败，忽略 query={}", truncate(query), e);
            }
        }
        String vector = PgVectorSupport.toVectorString(modelRouter.embed(query));
        if (key != null) {
            try {
                stringRedisTemplate
                        .opsForValue()
                        .set(key, vector, Duration.ofSeconds(EMBED_CACHE_TTL_SECONDS));
            } catch (Exception e) {
                log.debug("题库 RAG 向量缓存写入失败，忽略 query={}", truncate(query), e);
            }
        }
        return vector;
    }

    private String resultCacheKey(String query, QuestionFilter filter, int topK) {
        String category = filter == null ? "" : filter.category();
        String difficulty = filter == null ? "" : filter.difficulty();
        return cacheKey(
                RESULT_CACHE_PREFIX, query + "|" + category + "|" + difficulty + "|" + topK);
    }

    private String cacheKey(String prefix, String payload) {
        String md5 = DigestUtils.md5DigestAsHex(payload.getBytes(StandardCharsets.UTF_8));
        return prefix + md5;
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
