package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.PositionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 岗位 Mapper：继承 MyBatis-Plus BaseMapper，额外提供 embedding 自定义 SQL。 */
public interface PositionMapper extends BaseMapper<PositionEntity> {

    /** 更新 JD 向量（pgvector 需 {@code ::halfvec} 转换）。 */
    @Update(
            "UPDATE position SET embedding = #{embedding}::halfvec, updated_at = now() WHERE id ="
                    + " #{id}")
    int updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    /** 判断指定岗位是否已有向量。 */
    @Select("SELECT EXISTS(SELECT 1 FROM position WHERE id = #{id} AND embedding IS NOT NULL)")
    boolean existsEmbedding(@Param("id") Long id);
}
