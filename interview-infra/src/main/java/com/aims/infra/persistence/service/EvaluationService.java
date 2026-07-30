package com.aims.infra.persistence.service;

import com.aims.core.evaluation.DimensionAggregate;
import com.aims.infra.persistence.entity.EvaluationEntity;
import java.util.List;

/** 评估服务。 */
public interface EvaluationService {

    /** 对整个会话的所有轮次进行评估。 */
    void evaluateSession(Long sessionId);

    /** 查询会话所有评分。 */
    List<EvaluationEntity> listBySession(Long sessionId);

    /** 查询指定轮次的评分。 */
    List<EvaluationEntity> listByRound(Long roundId);

    /** 查询会话各维度聚合评分。 */
    DimensionAggregate aggregateDimensions(Long sessionId);
}
