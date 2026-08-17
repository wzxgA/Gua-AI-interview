package com.aims.infra.persistence.service.impl;

import com.aims.agent.DefaultEvaluatorAgent;
import com.aims.core.evaluation.DimensionAggregate;
import com.aims.core.evaluation.EvaluationContext;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.EvaluationEntity;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.mapper.EvaluationMapper;
import com.aims.infra.persistence.service.EvaluationService;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ResumeService;
import com.aims.infra.persistence.service.ResumeSummaryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 评估服务实现。 */
@Service
public class EvaluationServiceImpl implements EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationServiceImpl.class);

    private final EvaluationMapper evaluationMapper;
    private final InterviewSessionService sessionService;
    private final InterviewRoundService roundService;
    private final PositionService positionService;
    private final ResumeService resumeService;
    private final ResumeSummaryBuilder resumeSummaryBuilder;
    private final DefaultEvaluatorAgent evaluatorAgent;

    public EvaluationServiceImpl(
            EvaluationMapper evaluationMapper,
            InterviewSessionService sessionService,
            InterviewRoundService roundService,
            PositionService positionService,
            ResumeService resumeService,
            ResumeSummaryBuilder resumeSummaryBuilder,
            DefaultEvaluatorAgent evaluatorAgent) {
        this.evaluationMapper = evaluationMapper;
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.positionService = positionService;
        this.resumeService = resumeService;
        this.resumeSummaryBuilder = resumeSummaryBuilder;
        this.evaluatorAgent = evaluatorAgent;
    }

    @Override
    public void evaluateSession(Long sessionId) {
        // FE.15 P11 幂等守卫：Kafka at-least-once 下重复投递时评估已完成（REPORTING/DONE），
        // 跳过 AI 调用避免重复评估浪费 token。首次执行状态为 EVALUATING；失败重试耗尽置 FAILED 不命中守卫。
        InterviewSessionEntity existing = sessionService.getById(sessionId);
        String evalStatus = existing.getEvaluationStatus();
        if ("REPORTING".equals(evalStatus) || "DONE".equals(evalStatus)) {
            log.info("评估已完成，跳过重复消费 sessionId={} evaluationStatus={}", sessionId, evalStatus);
            return;
        }

        log.info("开始评估会话 sessionId={}", sessionId);
        sessionService.updateEvaluationStatus(sessionId, "EVALUATING");

        // 清理旧评分（支持重新评估）
        evaluationMapper.deleteBySession(sessionId);

        // 加载所有已回答轮次
        List<InterviewRoundEntity> rounds = roundService.listBySession(sessionId);
        List<InterviewRoundEntity> answered =
                rounds.stream()
                        .filter(r -> r.getAnswer() != null && !r.getAnswer().isBlank())
                        .collect(Collectors.toList());

        sessionService.updateTotalRoundsToEvaluate(sessionId, answered.size());

        // 加载岗位和简历
        InterviewSessionEntity session = sessionService.getById(sessionId);
        PositionEntity position = positionService.getById(session.getPositionId());
        ResumeEntity resume = resumeService.getById(session.getResumeId());
        String resumeSummary = resumeSummaryBuilder.build(resume);

        // 逐轮评估
        for (int i = 0; i < answered.size(); i++) {
            InterviewRoundEntity round = answered.get(i);
            try {
                EvaluationContext context =
                        new EvaluationContext(
                                sessionId,
                                round.getId(),
                                round.getSeq(),
                                round.getParentSeq(),
                                round.getFollowUpIndex(),
                                round.getQuestion(),
                                round.getAnswer(),
                                position.getTitle(),
                                position.getJdText(),
                                resumeSummary);

                List<RoundEvaluation> evaluations = evaluatorAgent.evaluate(context);

                // 批量写入评分记录
                List<EvaluationEntity> entities = new ArrayList<>();
                for (RoundEvaluation eval : evaluations) {
                    EvaluationEntity entity = new EvaluationEntity();
                    entity.setSessionId(sessionId);
                    entity.setRoundId(round.getId());
                    entity.setDimension(eval.dimension().name());
                    entity.setScore(eval.score());
                    entity.setComment(eval.comment());
                    entity.setEvidenceQuote(eval.evidenceQuote());
                    entities.add(entity);
                }
                evaluationMapper.batchInsert(entities);

                // 更新进度
                sessionService.updateEvaluatedRounds(sessionId, i + 1);
                log.info(
                        "轮次评估完成 sessionId={} roundId={} seq={} progress={}/{}",
                        sessionId,
                        round.getId(),
                        round.getSeq(),
                        i + 1,
                        answered.size());
            } catch (Exception e) {
                log.error(
                        "轮次评估失败 sessionId={} roundId={} seq={}",
                        sessionId,
                        round.getId(),
                        round.getSeq(),
                        e);
            }
        }

        // 评估完成，状态转为 REPORTING
        sessionService.updateStatus(sessionId, SessionStatus.REPORTING);
        sessionService.updateEvaluationStatus(sessionId, "REPORTING");
        log.info("评估完成，进入报告生成 sessionId={}", sessionId);
    }

    @Override
    public List<EvaluationEntity> listBySession(Long sessionId) {
        return evaluationMapper.listBySession(sessionId);
    }

    @Override
    public List<EvaluationEntity> listByRound(Long roundId) {
        return evaluationMapper.listByRound(roundId);
    }

    @Override
    public DimensionAggregate aggregateDimensions(Long sessionId) {
        List<EvaluationEntity> evaluations = listBySession(sessionId);
        DimensionAggregate aggregate = new DimensionAggregate();
        for (EvaluationEntity eval : evaluations) {
            EvaluationDimension dim = EvaluationDimension.valueOf(eval.getDimension());
            aggregate.add(dim, eval.getScore());
        }
        return aggregate;
    }
}
