package com.aims.infra.persistence.service.impl;

import com.aims.ai.router.ModelRouter;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.entity.ResumeSearchResult;
import com.aims.infra.persistence.service.ResumeRagService;
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
                    + "1 - (embedding <=> ?::vector) AS score "
                    + "FROM resume WHERE embedding IS NOT NULL";

    private static final RowMapper<ResumeSearchResult> ROW_MAPPER =
            (rs, rowNum) ->
                    new ResumeSearchResult(
                            rs.getLong("id"),
                            rs.getString("candidate_name"),
                            rs.getString("phone"),
                            rs.getString("email"),
                            rs.getDouble("score"));

    private final ModelRouter modelRouter;
    private final JdbcTemplate jdbcTemplate;

    public ResumeRagServiceImpl(ModelRouter modelRouter, JdbcTemplate jdbcTemplate) {
        this.modelRouter = modelRouter;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ResumeSearchResult> search(String query, int topK) {
        return search(query, null, topK);
    }

    @Override
    public List<ResumeSearchResult> search(String query, Long resumeId, int topK) {
        String vectorStr = PgVectorSupport.toVectorString(modelRouter.embed(query));
        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> params = new ArrayList<>();
        // SELECT 中的 ?::vector（计算相似度得分）
        params.add(vectorStr);

        if (resumeId != null) {
            sql.append(" AND id = ?");
            params.add(resumeId);
        }

        // ORDER BY 中的 ?::vector（按余弦距离排序）
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        params.add(vectorStr);
        params.add(topK);

        try {
            return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
        } catch (Exception e) {
            log.error("简历 RAG 检索失败 query={} resumeId={} topK={}", query, resumeId, topK, e);
            throw new BizException(ErrorCode.RAG_SEARCH_FAILED, "简历 RAG 检索失败", e);
        }
    }
}
