package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 面试会话 Mapper。 */
public interface InterviewSessionMapper extends BaseMapper<InterviewSessionEntity> {

    /** 保存面试计划 JSON（plan_json 为 JSONB 类型，需 ::jsonb 转型）。 */
    @Update(
            "UPDATE interview_session SET plan_json = #{planJson}::jsonb, updated_at = now()"
                    + " WHERE id = #{id}")
    int updatePlanJson(@Param("id") Long id, @Param("planJson") String planJson);
}
