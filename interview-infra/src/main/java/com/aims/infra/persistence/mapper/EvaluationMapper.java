package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.EvaluationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 评估结果 Mapper。 */
public interface EvaluationMapper extends BaseMapper<EvaluationEntity> {

    /** 批量插入评分记录。 */
    @Insert(
            "<script>"
                    + "INSERT INTO interview_evaluation (session_id, round_id, dimension, score, comment, evidence_quote) VALUES "
                    + "<foreach collection='list' item='item' separator=','>"
                    + "(#{item.sessionId}, #{item.roundId}, #{item.dimension}, #{item.score}, #{item.comment}, #{item.evidenceQuote})"
                    + "</foreach>"
                    + "</script>")
    int batchInsert(@Param("list") List<EvaluationEntity> list);

    /** 查询会话所有评分，按 round_id、dimension 排序。 */
    @Select(
            "SELECT * FROM interview_evaluation WHERE session_id = #{sessionId} ORDER BY round_id, dimension")
    List<EvaluationEntity> listBySession(@Param("sessionId") Long sessionId);

    /** 查询指定轮次的评分。 */
    @Select(
            "SELECT * FROM interview_evaluation WHERE round_id = #{roundId} ORDER BY dimension")
    List<EvaluationEntity> listByRound(@Param("roundId") Long roundId);

    /** 删除会话所有评分（重新评估时清理）。 */
    @Delete("DELETE FROM interview_evaluation WHERE session_id = #{sessionId}")
    int deleteBySession(@Param("sessionId") Long sessionId);
}
