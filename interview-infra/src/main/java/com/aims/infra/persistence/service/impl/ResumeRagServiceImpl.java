package com.aims.infra.persistence.service.impl;

import com.aims.ai.router.ModelRouter;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.ResumeSearchResult;
import com.aims.infra.persistence.service.ResumeRagService;
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
 * 简历 RAG 检索实现：向量检索 + 关键词匹配的混合检索（对齐题库侧实现）。
 *
 * <p>向量检索使用 pgvector 余弦距离，关键词匹配使用 pg_trgm ILIKE。最终得分为 {@code vectorScore * 0.7 + keywordScore *
 * 0.3}。如果关键词检索异常，退化为纯向量检索。检索结果与查询向量均走 Redis 缓存（均可配置关闭，Redis 异常自动降级直查）；
 * 查询向量缓存键与题库侧共用（`rag:embed:{md5(query)}`，向量仅取决于 query）。
 */
@Service
public class ResumeRagServiceImpl implements ResumeRagService {

    private static final Logger log = LoggerFactory.getLogger(ResumeRagServiceImpl.class);

    /** 向量得分权重。 */
    private static final double VECTOR_WEIGHT = 0.7;

    /** 关键词得分权重。 */
    private static final double KEYWORD_WEIGHT = 0.3;

    /** 结果缓存 TTL（秒）。 */
    private static final long RESULT_CACHE_TTL_SECONDS = 60;

    /** 查询向量缓存 TTL（秒）。 */
    private static final long EMBED_CACHE_TTL_SECONDS = 30 * 60;

    private static final String RESULT_CACHE_PREFIX = "rag:res:";

    /** 与题库侧共用：向量仅取决于 query。 */
    private static final String EMBED_CACHE_PREFIX = "rag:embed:";

    /** 命中字段标签（与 SQL CASE 分级、Java 侧字段顺序一致；v1.1-C §6：经历表优先）。 */
    private static final String[] FIELD_LABELS = {"work", "project", "raw_text", "skills", "name"};

    private static final TypeReference<List<ResumeSearchResult>> RESULT_LIST_TYPE =
            new TypeReference<>() {};

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    /**
     * v1.1-C §6：经历文本经 LATERAL 聚合（company+position / project name+role），关键词判定分级为 work(1.0) >
     * project(0.95) > raw_text(0.9) > skills(0.85) > name(0.8)。
     */
    private static final String BASE_SQL =
            "SELECT r.id, r.candidate_name, r.phone, r.email, "
                    + "r.parsed_json->>'currentTitle' AS current_title, "
                    + "(r.parsed_json->>'yearsOfExperience')::int AS years_of_experience, "
                    + "r.parsed_json->'skills' AS skills_json, "
                    + "r.raw_text, "
                    + "COALESCE(w.work_text, '') AS work_text, "
                    + "COALESCE(p.project_text, '') AS project_text, "
                    + "r.embedding_model, "
                    + "1 - (r.embedding <=> ?::halfvec) AS vector_score, "
                    + "CASE WHEN COALESCE(w.work_text, '') ILIKE '%' || ? || '%' THEN 1.0 "
                    + "WHEN COALESCE(p.project_text, '') ILIKE '%' || ? || '%' THEN 0.95 "
                    + "WHEN r.raw_text ILIKE '%' || ? || '%' THEN 0.9 "
                    + "WHEN COALESCE(r.parsed_json->>'skills', '') ILIKE '%' || ? || '%' THEN 0.85 "
                    + "WHEN r.candidate_name ILIKE '%' || ? || '%' THEN 0.8 "
                    + "ELSE 0.0 END AS keyword_score "
                    + "FROM resume r "
                    + "LEFT JOIN LATERAL (SELECT string_agg(COALESCE(we.company, '') || ' ' "
                    + "|| COALESCE(we.position, ''), ' ') AS work_text "
                    + "FROM resume_work_experience we WHERE we.resume_id = r.id) w ON TRUE "
                    + "LEFT JOIN LATERAL (SELECT string_agg(COALESCE(pe.name, '') || ' ' "
                    + "|| COALESCE(pe.role, ''), ' ') AS project_text "
                    + "FROM resume_project_experience pe WHERE pe.resume_id = r.id) p ON TRUE "
                    + "WHERE r.embedding IS NOT NULL";

    private final ModelRouter modelRouter;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rag.cache.result-enabled:true}")
    private boolean resultCacheEnabled;

    @Value("${rag.cache.embedding-enabled:true}")
    private boolean embeddingCacheEnabled;

    public ResumeRagServiceImpl(
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
    public RagSearchResponse<ResumeSearchResult> search(String query, int topK, Double minScore) {
        return search(query, null, topK, minScore);
    }

    @Override
    public RagSearchResponse<ResumeSearchResult> search(
            String query, Long resumeId, int topK, Double minScore) {
        long totalStart = System.nanoTime();

        // 1) 结果缓存
        String resultKey = resultCacheKey(query, resumeId, topK, minScore);
        List<ResumeSearchResult> cached = readResultCache(resultKey);
        if (cached != null) {
            long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.info(
                    "简历 RAG 命中结果缓存 query={} resumeId={} resultCount={} totalMs={}",
                    truncate(query),
                    resumeId,
                    cached.size(),
                    totalMs);
            return new RagSearchResponse<>(
                    cached, new RagSearchResponse.SearchMetrics(0, 0, totalMs, cached.size()));
        }

        // 2) 查询向量（带缓存，与题库共用）
        long embedStart = System.nanoTime();
        String vectorStr = embedCached(query);
        long embedMs = (System.nanoTime() - embedStart) / 1_000_000;

        // 3) 混合检索（失败降级纯向量）
        long sqlStart = System.nanoTime();
        List<ResumeSearchResult> results;
        String recallSource;
        try {
            results = hybridSearch(query, vectorStr, resumeId, topK);
            recallSource = "HYBRID";
        } catch (Exception e) {
            log.warn("简历混合检索失败，退化为纯向量检索 query={}", truncate(query), e);
            try {
                results = vectorOnlySearch(vectorStr, resumeId, topK);
                recallSource = "VECTOR";
            } catch (Exception ex) {
                long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
                log.error(
                        "简历 RAG 检索失败 query={} resumeId={} topK={} minScore={} embeddingMs={}"
                                + " totalMs={}",
                        query,
                        resumeId,
                        topK,
                        minScore,
                        embedMs,
                        totalMs,
                        ex);
                throw new BizException(ErrorCode.RAG_SEARCH_FAILED, "简历 RAG 检索失败", ex);
            }
        }
        long sqlMs = (System.nanoTime() - sqlStart) / 1_000_000;

        // 4) Java 侧重排 + minScore 过滤 + 截断 topK（复制列表，兼容不可变返回）
        List<ResumeSearchResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingDouble(ResumeSearchResult::score).reversed());
        if (minScore != null) {
            sorted = new ArrayList<>(sorted.stream().filter(r -> r.score() >= minScore).toList());
        }
        if (sorted.size() > topK) {
            sorted = new ArrayList<>(sorted.subList(0, topK));
        }
        results = sorted;

        // 5) 写结果缓存
        writeResultCache(resultKey, results);

        long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
        log.info(
                "简历 RAG 混合检索 query={} resumeId={} topK={} minScore={} recallSource={}"
                        + " embeddingMs={} sqlMs={} totalMs={} resultCount={}",
                truncate(query),
                resumeId,
                topK,
                minScore,
                recallSource,
                embedMs,
                sqlMs,
                totalMs,
                results.size());
        return new RagSearchResponse<>(
                results,
                new RagSearchResponse.SearchMetrics(embedMs, sqlMs, totalMs, results.size()));
    }

    /** 混合检索：SQL 同时计算向量得分和关键词得分。 */
    private List<ResumeSearchResult> hybridSearch(
            String query, String vectorStr, Long resumeId, int topK) {
        List<String> terms = tokenizeTerms(query);
        String keyword = sanitizeKeyword(query);

        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> params = new ArrayList<>();
        // SELECT 中的向量参数（计算 vector_score）
        params.add(vectorStr);
        // CASE WHEN 中的关键词参数（5 个 ILIKE 占位符，与分级顺序一致）
        for (int i = 0; i < 5; i++) {
            params.add(keyword);
        }

        if (resumeId != null) {
            sql.append(" AND r.id = ?");
            params.add(resumeId);
        }

        // ORDER BY 按混合得分排序（PostgreSQL 内计算），LIMIT 取 3 倍 topK 供 Java 侧重排
        sql.append(" ORDER BY (1 - (r.embedding <=> ?::halfvec)) * ")
                .append(VECTOR_WEIGHT)
                .append(" + CASE WHEN COALESCE(w.work_text, '') ILIKE '%' || ? || '%' THEN 1.0")
                .append(" WHEN COALESCE(p.project_text, '') ILIKE '%' || ? || '%' THEN 0.95")
                .append(" WHEN r.raw_text ILIKE '%' || ? || '%' THEN 0.9")
                .append(
                        " WHEN COALESCE(r.parsed_json->>'skills', '') ILIKE '%' || ? || '%' THEN"
                                + " 0.85")
                .append(" WHEN r.candidate_name ILIKE '%' || ? || '%' THEN 0.8 ELSE 0.0 END * ")
                .append(KEYWORD_WEIGHT)
                .append(" DESC LIMIT ?");
        params.add(vectorStr);
        for (int i = 0; i < 5; i++) {
            params.add(keyword);
        }
        params.add(topK * 3);

        return jdbcTemplate.query(
                sql.toString(), createRowMapper(terms, "HYBRID"), params.toArray());
    }

    /** 纯向量检索 fallback。 */
    private List<ResumeSearchResult> vectorOnlySearch(String vectorStr, Long resumeId, int topK) {
        String sql =
                "SELECT r.id, r.candidate_name, r.phone, r.email, "
                        + "r.parsed_json->>'currentTitle' AS current_title, "
                        + "(r.parsed_json->>'yearsOfExperience')::int AS years_of_experience, "
                        + "r.parsed_json->'skills' AS skills_json, "
                        + "r.raw_text, "
                        + "COALESCE(w.work_text, '') AS work_text, "
                        + "COALESCE(p.project_text, '') AS project_text, "
                        + "r.embedding_model, "
                        + "1 - (r.embedding <=> ?::halfvec) AS vector_score, "
                        + "0.0 AS keyword_score "
                        + "FROM resume r "
                        + "LEFT JOIN LATERAL (SELECT string_agg(COALESCE(we.company, '') || ' ' "
                        + "|| COALESCE(we.position, ''), ' ') AS work_text "
                        + "FROM resume_work_experience we WHERE we.resume_id = r.id) w ON TRUE "
                        + "LEFT JOIN LATERAL (SELECT string_agg(COALESCE(pe.name, '') || ' ' "
                        + "|| COALESCE(pe.role, ''), ' ') AS project_text "
                        + "FROM resume_project_experience pe WHERE pe.resume_id = r.id) p ON TRUE "
                        + "WHERE r.embedding IS NOT NULL";
        List<Object> params = new ArrayList<>();
        params.add(vectorStr);

        StringBuilder fullSql = new StringBuilder(sql);
        if (resumeId != null) {
            fullSql.append(" AND r.id = ?");
            params.add(resumeId);
        }
        fullSql.append(" ORDER BY r.embedding <=> ?::halfvec LIMIT ?");
        params.add(vectorStr);
        params.add(topK);

        return jdbcTemplate.query(
                fullSql.toString(), createRowMapper(List.of(), "VECTOR"), params.toArray());
    }

    private RowMapper<ResumeSearchResult> createRowMapper(List<String> terms, String recallSource) {
        return (rs, rowNum) -> {
            String skillsJson = rs.getString("skills_json");
            List<String> skills = parseSkills(skillsJson);
            String skillsText = String.join(" ", skills);
            double vectorScore = rs.getDouble("vector_score");
            double keywordScore = rs.getDouble("keyword_score");
            double finalScore = vectorScore * VECTOR_WEIGHT + keywordScore * KEYWORD_WEIGHT;
            String currentTitle = rs.getString("current_title");
            String candidateName = rs.getString("candidate_name");
            String rawText = rs.getString("raw_text");
            // v1.1-C §6：经历表聚合文本参与命中判定与高亮（work/project 标签优先于 raw_text）
            String workText = rs.getString("work_text");
            String projectText = rs.getString("project_text");

            List<String> matchedFields = new ArrayList<>();
            List<String> matchedTerms =
                    terms.stream()
                            .filter(
                                    t ->
                                            matchesAnyField(
                                                    workText,
                                                    projectText,
                                                    rawText,
                                                    skillsText,
                                                    candidateName,
                                                    t))
                            .toList();
            String highlight =
                    buildHighlight(
                            workText,
                            projectText,
                            rawText,
                            skillsText,
                            candidateName,
                            terms,
                            matchedFields);
            // 命中时用高亮片段解释"为什么命中"；未命中回退职位/技能摘要
            String matchedSnippet =
                    highlight != null ? highlight : buildMatchedSnippet(currentTitle, skills);

            return new ResumeSearchResult(
                    rs.getLong("id"),
                    candidateName,
                    rs.getString("phone"),
                    rs.getString("email"),
                    currentTitle,
                    rs.getObject("years_of_experience", Integer.class),
                    skills,
                    finalScore,
                    vectorScore,
                    keywordScore,
                    matchedSnippet,
                    rs.getString("embedding_model"),
                    matchedTerms,
                    matchedFields,
                    recallSource);
        };
    }

    private List<String> parseSkills(String skillsJson) {
        if (skillsJson == null || skillsJson.isBlank() || "null".equals(skillsJson)) {
            return List.of();
        }
        try {
            List<String> skills = objectMapper.readValue(skillsJson, STRING_LIST);
            return skills != null ? skills : List.of();
        } catch (Exception e) {
            log.warn("解析 skills JSON 失败: {}", skillsJson, e);
            return List.of();
        }
    }

    /** 未命中关键词时的回退摘要（职位 + 技能）。 */
    private String buildMatchedSnippet(String currentTitle, List<String> skills) {
        StringBuilder sb = new StringBuilder();
        if (currentTitle != null && !currentTitle.isBlank()) {
            sb.append("职位：").append(currentTitle);
        }
        if (skills != null && !skills.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append("技能：").append(String.join("、", skills));
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private boolean matchesAnyField(
            String workText,
            String projectText,
            String rawText,
            String skillsText,
            String name,
            String term) {
        if (term == null || term.length() < 2) return false;
        return containsIgnoreCase(workText, term)
                || containsIgnoreCase(projectText, term)
                || containsIgnoreCase(rawText, term)
                || containsIgnoreCase(skillsText, term)
                || containsIgnoreCase(name, term);
    }

    private boolean containsIgnoreCase(String text, String term) {
        return text != null && text.toLowerCase().contains(term);
    }

    /** 从命中的字段中截取高亮片段（首个命中词附近 ±30 字符），并记录命中字段。 */
    private String buildHighlight(
            String workText,
            String projectText,
            String rawText,
            String skillsText,
            String name,
            List<String> terms,
            List<String> matchedFields) {
        String[] fields = {workText, projectText, rawText, skillsText, name};
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
        // 转义 ILIKE 通配符
        return query.replace("%", "\\%").replace("_", "\\_");
    }

    // ---- Redis 缓存（与题库侧同款，均可配置关闭，异常自动降级直查） ----

    private List<ResumeSearchResult> readResultCache(String key) {
        if (!resultCacheEnabled || key == null) return null;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, RESULT_LIST_TYPE);
        } catch (Exception e) {
            log.debug("简历 RAG 结果缓存读取失败，忽略 key={}", key, e);
            return null;
        }
    }

    private void writeResultCache(String key, List<ResumeSearchResult> results) {
        if (!resultCacheEnabled || key == null || results == null || results.isEmpty()) return;
        try {
            stringRedisTemplate
                    .opsForValue()
                    .set(
                            key,
                            objectMapper.writeValueAsString(results),
                            Duration.ofSeconds(RESULT_CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.debug("简历 RAG 结果缓存写入失败，忽略 key={}", key, e);
        }
    }

    /** 查询向量：优先命中缓存（与题库共用键），miss 时调用模型并回填。 */
    private String embedCached(String query) {
        String key = embeddingCacheEnabled ? cacheKey(EMBED_CACHE_PREFIX, query) : null;
        if (key != null) {
            try {
                String cached = stringRedisTemplate.opsForValue().get(key);
                if (cached != null && !cached.isBlank()) {
                    return cached;
                }
            } catch (Exception e) {
                log.debug("简历 RAG 向量缓存读取失败，忽略 query={}", truncate(query), e);
            }
        }
        String vector = PgVectorSupport.toVectorString(modelRouter.embed(query));
        if (key != null) {
            try {
                stringRedisTemplate
                        .opsForValue()
                        .set(key, vector, Duration.ofSeconds(EMBED_CACHE_TTL_SECONDS));
            } catch (Exception e) {
                log.debug("简历 RAG 向量缓存写入失败，忽略 query={}", truncate(query), e);
            }
        }
        return vector;
    }

    private String resultCacheKey(String query, Long resumeId, int topK, Double minScore) {
        return cacheKey(RESULT_CACHE_PREFIX, query + "|" + resumeId + "|" + topK + "|" + minScore);
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
