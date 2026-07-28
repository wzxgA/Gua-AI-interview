package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.ResumeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 简历 Mapper。
 *
 * <p>embedding 列为 pgvector vector(1024) 类型，通过 {@link #updateEmbedding} 写入、 {@link #hasEmbedding}
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
}
