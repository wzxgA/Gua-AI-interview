package com.aims.infra.persistence.service;

import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.util.function.Function;

/** 面试会话持久化服务。 */
public interface InterviewSessionService {

    /** 创建面试会话（CREATED）。 */
    InterviewSessionEntity create(Long candidateId, Long positionId, String persona);

    /** 按候选人访问令牌查询会话（生成 accessToken），不存在抛 BizException。 */
    InterviewSessionEntity getByAccessToken(String accessToken);

    /** 设置/重置候选人访问密码（存储 bcrypt 哈希）。 */
    void updateAccessPassword(Long id, String passwordHash);

    /** 作废候选人入口（access_enabled = false）。 */
    void disableAccess(Long id);

    /** 更新入口模式（NONE / CANDIDATE_ONLY / DISABLED）。 */
    void updateAccessMode(Long id, String accessMode);

    /** 为会话生成候选人访问令牌（如尚未生成）。 */
    String ensureAccessToken(Long id);

    /** 查询会话详情，不存在抛 BizException。 */
    InterviewSessionEntity getById(Long id);

    /** 分页查询面试会话列表。 */
    IPage<InterviewSessionEntity> page(Page<InterviewSessionEntity> page, String status);

    /** 更新会话状态（校验合法迁移）。 */
    InterviewSessionEntity updateStatus(Long id, SessionStatus target);

    /** 原子条件状态转移。返回 true 表示成功转移，false 表示当前状态不匹配（已被其他线程转移）。 */
    boolean tryTransitionTo(
            Long id, SessionStatus target, SessionStatus from1, SessionStatus from2);

    /** 保存面试计划 JSON。 */
    InterviewSessionEntity savePlan(Long id, String planJson);

    /** 标记开始时间。 */
    InterviewSessionEntity markStarted(Long id);

    /** 标记结束时间。 */
    InterviewSessionEntity markEnded(Long id);

    /** 更新评估流程状态。 */
    void updateEvaluationStatus(Long sessionId, String evaluationStatus);

    /** 更新已评估轮次数。 */
    void updateEvaluatedRounds(Long sessionId, int evaluatedRounds);

    /** 更新需评估的总轮次数。 */
    void updateTotalRoundsToEvaluate(Long sessionId, int total);

    /** 更新综合得分。 */
    void updateTotalScore(Long sessionId, BigDecimal totalScore);

    /** 在事务中执行状态迁移回调。 */
    InterviewSessionEntity transition(
            Long id,
            SessionStatus expected,
            Function<InterviewSessionEntity, InterviewSessionEntity> action);

    /** 删除面试会话（级联删除轮次数据）。 */
    void delete(Long id);
}
