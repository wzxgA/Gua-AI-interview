package com.aims.infra.persistence.service;

import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import java.util.function.Function;

/** 面试会话持久化服务。 */
public interface InterviewSessionService {

    /** 创建面试会话（CREATED）。 */
    InterviewSessionEntity create(Long candidateId, Long positionId);

    /** 查询会话详情，不存在抛 BizException。 */
    InterviewSessionEntity getById(Long id);

    /** 更新会话状态（校验合法迁移）。 */
    InterviewSessionEntity updateStatus(Long id, SessionStatus target);

    /** 保存面试计划 JSON。 */
    InterviewSessionEntity savePlan(Long id, String planJson);

    /** 标记开始时间。 */
    InterviewSessionEntity markStarted(Long id);

    /** 标记结束时间。 */
    InterviewSessionEntity markEnded(Long id);

    /** 在事务中执行状态迁移回调。 */
    InterviewSessionEntity transition(
            Long id,
            SessionStatus expected,
            Function<InterviewSessionEntity, InterviewSessionEntity> action);
}
