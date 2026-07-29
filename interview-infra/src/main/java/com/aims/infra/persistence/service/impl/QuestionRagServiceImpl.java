package com.aims.infra.persistence.service.impl;

import com.aims.ai.router.ModelRouter;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.dto.QuestionFilter;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.aims.infra.persistence.service.QuestionRagService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 题库 RAG 检索实现：调用 {@link ModelRouter#embed} 生成查询向量，再用 JdbcTemplate 执行 pgvector 余弦距离检索。
 *
 * <p>使用 JdbcTemplate 而非 MyBatis-Plus：RAG 查询涉及 {@code ::vector} 类型转换与动态过滤拼接，JdbcTemplate 更灵活。
 */
@Service
public class QuestionRagServiceImpl implements QuestionRagService {

    private static final Logger log = LoggerFactory.getLogger(QuestionRagServiceImpl.class);

    private static final String BASE_SQL =
            "SELECT id, category, topic, difficulty, content, standard_answer, "
                    + "1 - (embedding <=> ?::vector) AS score "
                    + "FROM question_bank WHERE embedding IS NOT NULL";

    private static final RowMapper<QuestionSearchResult> ROW_MAPPER =
            (rs, rowNum) ->
                    new QuestionSearchResult(
                            rs.getLong("id"),
                            rs.getString("category"),
                            rs.getString("topic"),
                            rs.getString("difficulty"),
                            rs.getString("content"),
                            rs.getString("standard_answer"),
                            rs.getDouble("score"));

    private final ModelRouter modelRouter;
    private final JdbcTemplate jdbcTemplate;

    public QuestionRagServiceImpl(ModelRouter modelRouter, JdbcTemplate jdbcTemplate) {
        this.modelRouter = modelRouter;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<QuestionSearchResult> search(String query, int topK) {
        return search(query, null, topK);
    }

    @Override
    public List<QuestionSearchResult> search(String query, QuestionFilter filter, int topK) {
        long totalStart = System.nanoTime();
        long embedStart = System.nanoTime();
        String vectorStr = PgVectorSupport.toVectorString(modelRouter.embed(query));
        long embedMs = (System.nanoTime() - embedStart) / 1_000_000;

        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> params = new ArrayList<>();
        // SELECT 中的 ?::vector（计算相似度得分）
        params.add(vectorStr);

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

        // ORDER BY 中的 ?::vector（按余弦距离排序）
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        params.add(vectorStr);
        params.add(topK);

        long sqlStart = System.nanoTime();
        try {
            List<QuestionSearchResult> results =
                    jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
            long sqlMs = (System.nanoTime() - sqlStart) / 1_000_000;
            long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.info(
                    "题库 RAG 检索 query={} topK={} embeddingMs={} sqlMs={} totalMs={}"
                            + " resultCount={}",
                    truncate(query),
                    topK,
                    embedMs,
                    sqlMs,
                    totalMs,
                    results.size());
            return results;
        } catch (Exception e) {
            long sqlMs = (System.nanoTime() - sqlStart) / 1_000_000;
            long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
            log.error(
                    "题库 RAG 检索失败 query={} topK={} embeddingMs={} sqlMs={} totalMs={}",
                    query,
                    topK,
                    embedMs,
                    sqlMs,
                    totalMs,
                    e);
            throw new BizException(ErrorCode.RAG_SEARCH_FAILED, "题库 RAG 检索失败", e);
        }
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
