package com.aims.infra.persistence.service.impl;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.mapper.InterviewRoundMapper;
import com.aims.infra.persistence.mapper.InterviewSessionMapper;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 面试会话持久化服务实现。 */
@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewRoundMapper roundMapper;

    public InterviewSessionServiceImpl(
            InterviewSessionMapper sessionMapper, InterviewRoundMapper roundMapper) {
        this.sessionMapper = sessionMapper;
        this.roundMapper = roundMapper;
    }

    @Override
    @Transactional
    public InterviewSessionEntity create(Long candidateId, Long positionId) {
        if (candidateId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "候选人 ID 不能为空");
        }
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setCandidateId(candidateId);
        entity.setPositionId(positionId);
        entity.setStatus(SessionStatus.CREATED.name());
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        sessionMapper.insert(entity);
        return entity;
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
}
