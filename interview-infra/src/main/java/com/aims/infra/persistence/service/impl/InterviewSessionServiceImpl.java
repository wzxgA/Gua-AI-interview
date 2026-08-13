package com.aims.infra.persistence.service.impl;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.mapper.InterviewRoundMapper;
import com.aims.infra.persistence.mapper.InterviewSessionMapper;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 面试会话持久化服务实现。 */
@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {

    /** 候选人访问令牌长度（32 字节随机串，Base64 URL-safe 编码后 43 字符）。 */
    private static final int ACCESS_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final InterviewSessionMapper sessionMapper;
    private final InterviewRoundMapper roundMapper;

    public InterviewSessionServiceImpl(
            InterviewSessionMapper sessionMapper, InterviewRoundMapper roundMapper) {
        this.sessionMapper = sessionMapper;
        this.roundMapper = roundMapper;
    }

    @Override
    @Transactional
    public InterviewSessionEntity create(Long candidateId, Long positionId, String persona) {
        if (candidateId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "候选人 ID 不能为空");
        }
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setCandidateId(candidateId);
        entity.setPositionId(positionId);
        entity.setPersona(InterviewerPersona.fromString(persona).name());
        entity.setStatus(SessionStatus.CREATED.name());
        entity.setAccessEnabled(Boolean.FALSE);
        entity.setAccessMode("NONE");
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        sessionMapper.insert(entity);
        return entity;
    }

    @Override
    public InterviewSessionEntity getByAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "访问令牌不能为空");
        }
        InterviewSessionEntity entity =
                sessionMapper.selectOne(
                        new LambdaQueryWrapper<InterviewSessionEntity>()
                                .eq(InterviewSessionEntity::getAccessToken, accessToken));
        if (entity == null || Boolean.FALSE.equals(entity.getAccessEnabled())) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND, "面试链接不存在或已失效");
        }
        return entity;
    }

    @Override
    @Transactional
    public void updateAccessPassword(Long id, String passwordHash) {
        InterviewSessionEntity entity = getById(id);
        entity.setAccessPassword(passwordHash);
        entity.setAccessEnabled(Boolean.TRUE);
        entity.setUpdatedAt(Instant.now());
        sessionMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void disableAccess(Long id) {
        InterviewSessionEntity entity = getById(id);
        entity.setAccessEnabled(Boolean.FALSE);
        entity.setAccessMode("DISABLED");
        entity.setUpdatedAt(Instant.now());
        sessionMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void updateAccessMode(Long id, String accessMode) {
        InterviewSessionEntity entity = getById(id);
        entity.setAccessMode(accessMode);
        entity.setUpdatedAt(Instant.now());
        sessionMapper.updateById(entity);
    }

    @Override
    @Transactional
    public String ensureAccessToken(Long id) {
        InterviewSessionEntity entity = getById(id);
        if (entity.getAccessToken() == null || entity.getAccessToken().isBlank()) {
            entity.setAccessToken(generateAccessToken());
            entity.setUpdatedAt(Instant.now());
            sessionMapper.updateById(entity);
        }
        return entity.getAccessToken();
    }

    /** 生成高熵访问令牌（32 字节随机 → Base64 URL-safe），不可枚举。 */
    private String generateAccessToken() {
        byte[] bytes = new byte[ACCESS_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public InterviewSessionEntity getById(Long id) {
        InterviewSessionEntity entity = sessionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND, "面试会话不存在: " + id);
        }
        return entity;
    }

    @Override
    public IPage<InterviewSessionEntity> page(Page<InterviewSessionEntity> page, String status) {
        LambdaQueryWrapper<InterviewSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InterviewSessionEntity::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(InterviewSessionEntity::getStatus, status);
        }
        return sessionMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public InterviewSessionEntity updateStatus(Long id, SessionStatus target) {
        InterviewSessionEntity entity = getById(id);
        SessionStatus current = SessionStatus.valueOf(entity.getStatus());
        if (!current.canTransitionTo(target)) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        entity.setStatus(target.name());
        entity.setUpdatedAt(Instant.now());
        sessionMapper.updateById(entity);
        return entity;
    }

    @Override
    public boolean tryTransitionTo(
            Long id, SessionStatus target, SessionStatus from1, SessionStatus from2) {
        int affected =
                sessionMapper.tryTransitionStatus(id, target.name(), from1.name(), from2.name());
        return affected > 0;
    }

    @Override
    @Transactional
    public InterviewSessionEntity savePlan(Long id, String planJson) {
        InterviewSessionEntity entity = getById(id);
        sessionMapper.updatePlanJson(id, planJson);
        entity.setPlanJson(planJson);
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    @Override
    @Transactional
    public InterviewSessionEntity markStarted(Long id) {
        InterviewSessionEntity entity = getById(id);
        Instant now = Instant.now();
        entity.setStartedAt(now);
        entity.setUpdatedAt(now);
        sessionMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public InterviewSessionEntity markEnded(Long id) {
        InterviewSessionEntity entity = getById(id);
        Instant now = Instant.now();
        entity.setEndedAt(now);
        entity.setUpdatedAt(now);
        sessionMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public InterviewSessionEntity transition(
            Long id,
            SessionStatus expected,
            Function<InterviewSessionEntity, InterviewSessionEntity> action) {
        InterviewSessionEntity entity = getById(id);
        SessionStatus current = SessionStatus.valueOf(entity.getStatus());
        if (current != expected) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        entity = action.apply(entity);
        sessionMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 校验存在
        getById(id);
        // 级联删除轮次数据
        LambdaQueryWrapper<InterviewRoundEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewRoundEntity::getSessionId, id);
        roundMapper.delete(wrapper);
        sessionMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void updateEvaluationStatus(Long sessionId, String evaluationStatus) {
        sessionMapper.updateEvaluationStatus(sessionId, evaluationStatus);
    }

    @Override
    @Transactional
    public void updateEvaluatedRounds(Long sessionId, int evaluatedRounds) {
        sessionMapper.updateEvaluatedRounds(sessionId, evaluatedRounds);
    }

    @Override
    @Transactional
    public void updateTotalRoundsToEvaluate(Long sessionId, int total) {
        sessionMapper.updateTotalRoundsToEvaluate(sessionId, total);
    }

    @Override
    @Transactional
    public void updateTotalScore(Long sessionId, BigDecimal totalScore) {
        sessionMapper.updateTotalScore(sessionId, totalScore);
    }
}
