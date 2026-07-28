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

    /** 查询会话的轮次数量（已回答）。 */
    @Select(
            "SELECT COUNT(*) FROM interview_round WHERE session_id = #{sessionId} AND answer IS NOT"
                    + " NULL")
    int countAnswered(@Param("sessionId") Long sessionId);
}
