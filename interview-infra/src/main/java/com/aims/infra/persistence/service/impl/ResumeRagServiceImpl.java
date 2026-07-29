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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 简历 RAG 检索实现：向量检索 + 关键词匹配的混合检索。
 *
 * <p>向量检索使用 pgvector 余弦距离，关键词匹配使用 pg_trgm ILIKE。最终得分为 {@code vectorScore * 0.7 + keywordScore *
 * 0.3}。如果关键词检索异常，退化为纯向量检索。
 */
@Service
public class ResumeRagServiceImpl implements ResumeRagService {

    private static final Logger log = LoggerFactory.getLogger(ResumeRagServiceImpl.class);

    /** 向量得分权重。 */
    private static final double VECTOR_WEIGHT = 0.7;

    /** 关键词得分权重。 */
    private static final double KEYWORD_WEIGHT = 0.3;

    private static final String BASE_SQL =
            "SELECT id, candidate_name, phone, email, "
                    + "parsed_json->>'currentTitle' AS current_title, "
                    + "(parsed_json->>'yearsOfExperience')::int AS years_of_experience, "
                    + "parsed_json->'skills' AS skills_json, "
                    + "embedding_model, "
                    + "1 - (embedding <=> ?::halfvec) AS vector_score, "
                    + "CASE WHEN raw_text ILIKE '%' || ? || '%' THEN 1.0 "
                    + "WHEN COALESCE(parsed_json->>'skills', '') ILIKE '%' || ? || '%' THEN 0.9 "
                    + "WHEN candidate_name ILIKE '%' || ? || '%' THEN 0.8 "
                    + "ELSE 0.0 END AS keyword_score "
                    + "FROM resume WHERE embedding IS NOT NULL";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final RowMapper<ResumeSearchResult> rowMapper;

    private final ModelRouter modelRouter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ResumeRagServiceImpl(
            ModelRouter modelRouter, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.modelRouter = modelRouter;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.rowMapper = createRowMapper();
    }

    private RowMapper<ResumeSearchResult> createRowMapper() {
        return (rs, rowNum) -> {
            String skillsJson = rs.getString("skills_json");
            List<String> skills = parseSkills(skillsJson);
            double vectorScore = rs.getDouble("vector_score");
            double keywordScore = rs.getDouble("keyword_score");
            double finalScore = vectorScore * VECTOR_WEIGHT + keywordScore * KEYWORD_WEIGHT;
            String currentTitle = rs.getString("current_title");

            return new ResumeSearchResult(
                    rs.getLong("id"),
                    rs.getString("candidate_name"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    currentTitle,
                    rs.getObject("years_of_experience", Integer.class),
                    skills,
                    finalScore,
                    vectorScore,
                    keywordScore,
                    buildMatchedSnippet(currentTitle, skills),
                    rs.getString("embedding_model"));
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

    @Override
    public RagSearchResponse<ResumeSearchResult> search(String query, int topK, Double minScore) {
        return search(query, null, topK, minScore);
    }

    @Override
    public RagSearchResponse<ResumeSearchResult> search(
            String query, Long resumeId, int topK, Double minScore) {
        long totalStart = System.nanoTime();
        long embedStart = System.nanoTime();
        String vectorStr = PgVectorSupport.toVectorString(modelRouter.embed(query));
        long embedMs = (System.nanoTime() - embedStart) / 1_000_000;

        long sqlStart = System.nanoTime();
        List<ResumeSearchResult> results;
        try {
            results = hybridSearch(query, vectorStr, resumeId, topK);
        } catch (Exception e) {
            log.warn("混合检索失败，退化为纯向量检索 query={}", truncate(query), e);
            try {
                results = vectorOnlySearch(vectorStr, resumeId, topK);
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

        // Java 侧按混合得分排序（SQL ORDER BY 可能无法正确排序混合得分）
        results.sort(Comparator.comparingDouble(ResumeSearchResult::score).reversed());

        // 应用 minScore 过滤（基于混合最终得分）
        if (minScore != null) {
            results = results.stream().filter(r -> r.score() >= minScore).toList();
        }

        // 限制 topK
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
        log.info(
                "简历 RAG 混合检索 query={} resumeId={} topK={} minScore={} embeddingMs={} sqlMs={}"
                        + " totalMs={} resultCount={}",
                truncate(query),
                resumeId,
                topK,
                minScore,
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
        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> params = new ArrayList<>();
        // SELECT 中的向量参数（计算 vector_score）
        params.add(vectorStr);
        // CASE WHEN 中的关键词参数（3 个 ILIKE 占位符）
        String keyword = sanitizeKeyword(query);
        params.add(keyword);
        params.add(keyword);
        params.add(keyword);

        if (resumeId != null) {
            sql.append(" AND id = ?");
            params.add(resumeId);
        }

        // ORDER BY 按混合得分排序（PostgreSQL 内计算）
        sql.append(" ORDER BY (1 - (embedding <=> ?::halfvec)) * ")
                .append(VECTOR_WEIGHT)
                .append(" + CASE WHEN raw_text ILIKE '%' || ? || '%' THEN 1.0")
                .append(" WHEN COALESCE(parsed_json->>'skills', '') ILIKE '%' || ? || '%' THEN 0.9")
                .append(" WHEN candidate_name ILIKE '%' || ? || '%' THEN 0.8 ELSE 0.0 END * ")
                .append(KEYWORD_WEIGHT)
                .append(" DESC LIMIT ?");
        params.add(vectorStr);
        params.add(keyword);
        params.add(keyword);
        params.add(keyword);
        // 取 3 倍 topK 再在 Java 侧重新排序，确保不遗漏高关键词得分结果
        params.add(topK * 3);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /** 纯向量检索 fallback。 */
    private List<ResumeSearchResult> vectorOnlySearch(String vectorStr, Long resumeId, int topK) {
        String sql =
                "SELECT id, candidate_name, phone, email, "
                        + "parsed_json->>'currentTitle' AS current_title, "
                        + "(parsed_json->>'yearsOfExperience')::int AS years_of_experience, "
                        + "parsed_json->'skills' AS skills_json, "
                        + "embedding_model, "
                        + "1 - (embedding <=> ?::halfvec) AS vector_score, "
                        + "0.0 AS keyword_score "
                        + "FROM resume WHERE embedding IS NOT NULL";
        List<Object> params = new ArrayList<>();
        params.add(vectorStr);

        StringBuilder fullSql = new StringBuilder(sql);
        if (resumeId != null) {
            fullSql.append(" AND id = ?");
            params.add(resumeId);
        }
        fullSql.append(" ORDER BY embedding <=> ?::halfvec LIMIT ?");
        params.add(vectorStr);
        params.add(topK);

        return jdbcTemplate.query(fullSql.toString(), rowMapper, params.toArray());
    }

    /** 清理关键词，防止 SQL 注入（ILIKE 的 % 和 _ 转义）。 */
    private String sanitizeKeyword(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        // 转义 ILIKE 通配符
        return query.replace("%", "\\%").replace("_", "\\_");
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
