package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.ResumeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 简历 Mapper。
 *
 * <p>embedding 列为 pgvector vector(2048) 类型，通过 {@link #updateEmbedding} 写入、 {@link #hasEmbedding}
 * 检查存在性，绕过 MyBatis-Plus 自动映射。
 */
public interface ResumeMapper extends BaseMapper<ResumeEntity> {

    /**
     * 更新简历向量（pgvector 字符串格式 "[0.1,0.2,...]"）。
     *
     * @param id 简历 ID
     * @param embedding pgvector 字符串
     * @return 影响行数
     */
    @Update(
            "UPDATE resume SET embedding = #{embedding}::vector, updated_at = now() WHERE id ="
                    + " #{id}")
    int updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    /**
     * 检查简历是否已生成向量。
     *
     * @param id 简历 ID
     * @return true=已生成；false/null=未生成或简历不存在
     */
    @Select("SELECT embedding IS NOT NULL FROM resume WHERE id = #{id}")
    Boolean hasEmbedding(@Param("id") Long id);

    /** 抢占简历解析任务，避免并发重复调用 AI。 */
    @Update(
            "UPDATE resume SET parse_status = 'PROCESSING', parse_attempts = parse_attempts + 1,"
                    + " parse_error = NULL, updated_at = now() WHERE id = #{id}"
                    + " AND parse_status IN ('PENDING', 'FAILED')")
    int claimParse(@Param("id") Long id);

    /** 记录解析成功。 */
    @Update(
            "UPDATE resume SET parsed_json = #{parsedJson}::jsonb, parse_status = 'PARSED',"
                    + " parse_error = NULL, parsed_at = now(), updated_at = now() WHERE id = #{id}")
    int markParsed(@Param("id") Long id, @Param("parsedJson") String parsedJson);

    /** 记录解析失败。 */
    @Update(
            "UPDATE resume SET parse_status = 'FAILED', parse_error = #{error},"
                    + " updated_at = now() WHERE id = #{id}")
    int markParseFailed(@Param("id") Long id, @Param("error") String error);

    /** 抢占简历向量化任务。 */
    @Update(
            "UPDATE resume SET embedding_status = 'PROCESSING',"
                    + " embedding_attempts = embedding_attempts + 1, embedding_error = NULL,"
                    + " updated_at = now() WHERE id = #{id} AND parse_status = 'PARSED'"
                    + " AND embedding_status IN ('PENDING', 'FAILED')")
    int claimEmbedding(@Param("id") Long id);

    /** 写入向量并标记向量化成功。 */
    @Update(
            "UPDATE resume SET embedding = #{embedding}::vector, embedding_status = 'COMPLETED',"
                    + " embedding_error = NULL, embedding_model = #{model},"
                    + " embedding_dimension = #{dimension}, embedded_at = now(), updated_at = now()"
                    + " WHERE id = #{id}")
    int markEmbedded(
            @Param("id") Long id,
            @Param("embedding") String embedding,
            @Param("model") String model,
            @Param("dimension") int dimension);

    /** 记录向量化失败。 */
    @Update(
            "UPDATE resume SET embedding_status = 'FAILED', embedding_error = #{error},"
                    + " updated_at = now() WHERE id = #{id}")
    int markEmbeddingFailed(@Param("id") Long id, @Param("error") String error);

    /** 使旧向量失效并允许重新生成。 */
    @Update(
            "UPDATE resume SET embedding = NULL, embedding_status = 'PENDING',"
                    + " embedding_model = NULL, embedding_dimension = NULL,"
                    + " embedded_at = NULL, embedding_error = NULL, updated_at = now()"
                    + " WHERE id = #{id}")
    int invalidateEmbedding(@Param("id") Long id);
}
