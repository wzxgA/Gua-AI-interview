package com.aims.infra.persistence.service.impl;

import com.aims.ai.router.ModelRouter;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.entity.ResumeSearchResult;
import com.aims.infra.persistence.service.ResumeRagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 简历 RAG 检索实现：调用 {@link ModelRouter#embed} 生成查询向量，再用 JdbcTemplate 执行 pgvector 余弦距离检索。
 *
 * <p>使用 JdbcTemplate 而非 MyBatis-Plus：RAG 查询涉及 {@code ::vector} 类型转换与动态过滤拼接，JdbcTemplate 更灵活。
 */
@Service
public class ResumeRagServiceImpl implements ResumeRagService {

    private static final Logger log = LoggerFactory.getLogger(ResumeRagServiceImpl.class);

    private static final String BASE_SQL =
            "SELECT id, candidate_name, phone, email, "
                    + "parsed_json->>'currentTitle' AS current_title, "
                    + "(parsed_json->>'yearsOfExperience')::int AS years_of_experience, "
                    + "parsed_json->'skills' AS skills_json, "
                    + "embedding_model, "
                    + "1 - (embedding <=> ?::vector) AS score "
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

            return new ResumeSearchResult(
                    rs.getLong("id"),
                    rs.getString("candidate_name"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("current_title"),
                    rs.getObject("years_of_experience", Integer.class),
                    skills,
                    rs.getDouble("score"),
                    buildMatchedSnippet(rs.getString("current_title"), skills),
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
    public List<ResumeSearchResult> search(String query, int topK, Double minScore) {
        return search(query, null, topK, minScore);
    }

    @Override
    public List<ResumeSearchResult> search(String query, Long resumeId, int topK, Double minScore) {
        long totalStart = System.nanoTime();
        long embedStart = System.nanoTime();
        String vectorStr = PgVectorSupport.toVectorString(modelRouter.embed(query));
        long embedMs = (System.nanoTime() - embedStart) / 1_000_000;

        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> params = new ArrayList<>();
        // SELECT 中的 ?::vector（计算相似度得分）
        params.add(vectorStr);

        if (minScore != null) {
            sql.append(" AND 1 - (embedding <=> ?::vector) >= ?");
            params.add(vectorStr);
            params.add(minScore);
        }

        if (resumeId != null) {
            sql.append(" AND id = ?");
            params.add(resumeId);
        }

        // ORDER BY 中的 ?::vector（按余弦距离排序）
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        params.add(vectorStr);
        params.add(topK);

        long sqlStart = System.nanoTime();
        try {
            List<ResumeSearchResult> results =
                    jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
            long sqlMs = (System.nanoTime() - sqlStart) / 1_000_000;
            long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.info(
                    "简历 RAG 检索 query={} resumeId={} topK={} minScore={} embeddingMs={} sqlMs={}"
                            + " totalMs={} resultCount={}",
                    truncate(query),
                    resumeId,
                    topK,
                    minScore,
                    embedMs,
                    sqlMs,
                    totalMs,
                    results.size());
            return results;
        } catch (Exception e) {
            long sqlMs = (System.nanoTime() - sqlStart) / 1_000_000;
            long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.error(
                    "简历 RAG 检索失败 query={} resumeId={} topK={} minScore={} embeddingMs={}"
                            + " sqlMs={} totalMs={}",
                    query,
                    resumeId,
                    topK,
                    minScore,
                    embedMs,
                    sqlMs,
                    totalMs,
                    e);
            throw new BizException(ErrorCode.RAG_SEARCH_FAILED, "简历 RAG 检索失败", e);
        }
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
