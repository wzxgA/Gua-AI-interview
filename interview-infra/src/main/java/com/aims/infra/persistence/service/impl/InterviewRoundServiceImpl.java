package com.aims.infra.persistence.service.impl;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.mapper.InterviewRoundMapper;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 面试轮次持久化服务实现。 */
@Service
public class InterviewRoundServiceImpl implements InterviewRoundService {

    private final InterviewRoundMapper roundMapper;

    public InterviewRoundServiceImpl(InterviewRoundMapper roundMapper) {
        this.roundMapper = roundMapper;
    }

    @Override
    @Transactional
    public InterviewRoundEntity createRound(Long sessionId, int seq, String question) {
        InterviewRoundEntity entity = new InterviewRoundEntity();
        entity.setSessionId(sessionId);
        entity.setSeq(seq);
        entity.setQuestion(question);
        entity.setCreatedAt(Instant.now());
        roundMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public InterviewRoundEntity updateAnswer(Long roundId, String answer) {
        InterviewRoundEntity entity = roundMapper.selectById(roundId);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "面试轮次不存在: " + roundId);
        }
        entity.setAnswer(answer);
        roundMapper.updateById(entity);
        return entity;
    }

    @Override
    public List<InterviewRoundEntity> listBySession(Long sessionId) {
        return roundMapper.selectList(
                Wrappers.<InterviewRoundEntity>lambdaQuery()
                        .eq(InterviewRoundEntity::getSessionId, sessionId)
                        .orderByAsc(InterviewRoundEntity::getSeq));
    }

    @Override
    public int countAnswered(Long sessionId) {
        return roundMapper.countAnswered(sessionId);
    }

    @Override
    public int maxSeq(Long sessionId) {
        return roundMapper.maxSeq(sessionId);
    }
}
