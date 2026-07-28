package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.QuestionEntity;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 题库 Mapper。
 *
 * <p>embedding 列为 pgvector vector 类型，通过自定义 SQL（{@code ::vector} 转型）写入， 不依赖 MyBatis-Plus 自动 CRUD。
 */
public interface QuestionMapper extends BaseMapper<QuestionEntity> {

    /**
     * 更新题目向量。
     *
     * @param id 题目 ID
     * @param embedding pgvector 字符串格式（如 {@code "[0.1,0.2,0.3]"}）
     * @return 受影响行数
     */
    @Update(
            "UPDATE question_bank SET embedding = #{embedding}::vector, updated_at = now() WHERE id"
                    + " = #{id}")
    int updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    /**
     * 向量相似度检索。
     *
     * @param queryVector pgvector 字符串格式查询向量
     * @param topK 返回条数
     * @return 按相似度降序排列的检索结果
     */
    @Select(
            "SELECT id, category, topic, difficulty, content, "
                    + "standard_answer AS standardAnswer, "
                    + "1 - (embedding <=> #{queryVector}::vector) AS score "
                    + "FROM question_bank WHERE embedding IS NOT NULL "
                    + "ORDER BY embedding <=> #{queryVector}::vector LIMIT #{topK}")
    List<QuestionSearchResult> searchByVector(
            @Param("queryVector") String queryVector, @Param("topK") int topK);

    /**
     * 查询未向量化的题目（仅取 id + content，避免 text[] 列的 TypeHandler 问题）。
     *
     * @param limit 单批最大数量
     * @return 未向量化题目列表
     */
    @Select("SELECT id, content FROM question_bank WHERE embedding IS NULL LIMIT #{limit}")
    List<QuestionEntity> selectWithoutEmbedding(@Param("limit") int limit);
}
