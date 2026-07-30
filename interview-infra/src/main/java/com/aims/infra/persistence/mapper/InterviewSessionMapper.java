package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 面试会话 Mapper。 */
public interface InterviewSessionMapper extends BaseMapper<InterviewSessionEntity> {

    /** 保存面试计划 JSON（plan_json 为 JSONB 类型，需 ::jsonb 转型）。 */
    @Update(
            "UPDATE interview_session SET plan_json = #{planJson}::jsonb, updated_at = now()"
                    + " WHERE id = #{id}")
    int updatePlanJson(@Param("id") Long id, @Param("planJson") String planJson);

    /** 更新评估流程状态。 */
    @Update(
            "UPDATE interview_session SET evaluation_status = #{status}, updated_at = now() WHERE id = #{id}")
    int updateEvaluationStatus(@Param("id") Long id, @Param("status") String status);

    /** 更新已评估轮次数。 */
    @Update(
            "UPDATE interview_session SET evaluated_rounds = #{evaluatedRounds}, updated_at = now() WHERE id = #{id}")
    int updateEvaluatedRounds(@Param("id") Long id, @Param("evaluatedRounds") int evaluatedRounds);

    /** 更新需评估的总轮次数。 */
    @Update(
            "UPDATE interview_session SET total_rounds_to_evaluate = #{total}, updated_at = now() WHERE id = #{id}")
    int updateTotalRoundsToEvaluate(@Param("id") Long id, @Param("total") int total);

    /** 更新综合得分。 */
    @Update(
            "UPDATE interview_session SET total_score = #{score}, updated_at = now() WHERE id = #{id}")
    int updateTotalScore(@Param("id") Long id, @Param("score") BigDecimal score);
}
