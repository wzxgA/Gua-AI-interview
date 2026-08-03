package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 面试轮次 Mapper。 */
public interface InterviewRoundMapper extends BaseMapper<InterviewRoundEntity> {

    /** 查询会话当前最大序号。 */
    @Select("SELECT COALESCE(MAX(seq), 0) FROM interview_round WHERE session_id = #{sessionId}")
    int maxSeq(@Param("sessionId") Long sessionId);

    /** 查询会话的主问题数量（已回答，不含追问）。 */
    @Select(
            "SELECT COUNT(*) FROM interview_round WHERE session_id = #{sessionId}"
                    + " AND parent_seq IS NULL AND answer IS NOT NULL AND answer <> ''")
    int countAnswered(@Param("sessionId") Long sessionId);

    /** 查询某个主问题下的追问次数。 */
    @Select(
            "SELECT COUNT(*) FROM interview_round WHERE session_id = #{sessionId}"
                    + " AND parent_seq = #{parentSeq}")
    int countFollowUps(@Param("sessionId") Long sessionId, @Param("parentSeq") int parentSeq);
}
