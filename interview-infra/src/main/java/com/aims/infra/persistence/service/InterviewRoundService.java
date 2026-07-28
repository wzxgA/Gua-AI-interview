package com.aims.infra.persistence.service;

import com.aims.infra.persistence.entity.InterviewRoundEntity;
import java.util.List;

/** 面试轮次持久化服务。 */
public interface InterviewRoundService {

    /** 创建新轮次（question 已知，answer 为空）。返回实体含 ID。 */
    InterviewRoundEntity createRound(Long sessionId, int seq, String question);

    /** 更新轮次回答。 */
    InterviewRoundEntity updateAnswer(Long roundId, String answer);

    /** 查询会话所有轮次，按 seq 排序。 */
    List<InterviewRoundEntity> listBySession(Long sessionId);

    /** 查询会话已回答的轮次数。 */
    int countAnswered(Long sessionId);

    /** 查询会话当前最大序号。 */
    int maxSeq(Long sessionId);
}
