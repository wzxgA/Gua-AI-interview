package com.aims.infra.persistence.service.impl;

import com.aims.agent.DefaultReportAgent;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.core.evaluation.DimensionAggregate;
import com.aims.core.evaluation.EvaluationDimension;
import com.aims.core.report.ReportContext;
import com.aims.core.report.ReportResult;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.EvaluationEntity;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.ReportEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.mapper.ReportMapper;
import com.aims.infra.persistence.service.EvaluationService;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ReportService;
import com.aims.infra.persistence.service.ResumeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 报告服务实现。 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);
    private static final int RESUME_SUMMARY_MAX = 2000;

    private final ReportMapper reportMapper;
    private final EvaluationService evaluationService;
    private final InterviewSessionService sessionService;
    private final InterviewRoundService roundService;
    private final PositionService positionService;
    private final ResumeService resumeService;
    private final DefaultReportAgent reportAgent;
    private final ObjectMapper objectMapper;

    public ReportServiceImpl(
            ReportMapper reportMapper,
            EvaluationService evaluationService,
            InterviewSessionService sessionService,
            InterviewRoundService roundService,
            PositionService positionService,
            ResumeService resumeService,
            DefaultReportAgent reportAgent,
            ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.evaluationService = evaluationService;
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.positionService = positionService;
        this.resumeService = resumeService;
        this.reportAgent = reportAgent;
        this.objectMapper = objectMapper;
    }

    @Override
    public void generateReport(Long sessionId) {
        log.info("开始生成报告 sessionId={}", sessionId);

        // 加载全部评分
        List<EvaluationEntity> evaluations = evaluationService.listBySession(sessionId);

        // 聚合各维度平均分
        DimensionAggregate aggregate = evaluationService.aggregateDimensions(sessionId);

        // 计算综合加权得分
        double weightedScore = calculateWeightedScore(aggregate);
        log.info("综合加权得分 sessionId={} score={}", sessionId, String.format("%.2f", weightedScore));

        // 加载会话信息
        InterviewSessionEntity session = sessionService.getById(sessionId);
        PositionEntity position = positionService.getById(session.getPositionId());
        ResumeEntity resume = resumeService.getById(session.getCandidateId());

        // 构建对话摘要
        String conversationSummary = buildConversationSummary(sessionId);

        // 构建评分汇总
        List<ReportContext.EvaluationSummary> evalSummaries = evaluations.stream()
                .map(e -> new ReportContext.EvaluationSummary(
                        0, // seq 由 listBySession 排序决定，此处不需要
                        e.getDimension(),
                        e.getScore(),
                        e.getComment(),
                        e.getEvidenceQuote()))
                .collect(Collectors.toList());

        // 调用 ReportAgent 生成报告
        ReportContext context = new ReportContext(
                sessionId,
                resume.getCandidateName(),
                position.getTitle(),
                position.getJdText(),
                buildResumeSummary(resume),
                evalSummaries,
                conversationSummary);
        ReportResult result = reportAgent.generate(context, aggregate, weightedScore);

        // 序列化 dimensionsJson
        String dimensionsJson;
        try {
            dimensionsJson = objectMapper.writeValueAsString(aggregate.getAll());
        } catch (JsonProcessingException e) {
            log.error("序列化维度聚合结果失败 sessionId={}", sessionId, e);
            dimensionsJson = "{}";
        }

        // 保存报告
        ReportEntity entity = new ReportEntity();
        entity.setSessionId(sessionId);
        entity.setSummary(result.summary());
        entity.setDimensionsJson(dimensionsJson);
        entity.setRecommendation(result.recommendation().name());
        reportMapper.upsert(entity);

        // 更新会话总分
        sessionService.updateTotalScore(sessionId, BigDecimal.valueOf(weightedScore));

        // 状态转为 COMPLETED
        sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);
        sessionService.markEnded(sessionId);
        sessionService.updateEvaluationStatus(sessionId, "DONE");
        log.info("报告生成完成 sessionId={}", sessionId);
    }

    @Override
    public ReportEntity getBySession(Long sessionId) {
        // 校验会话存在
        sessionService.getById(sessionId);
        ReportEntity report = reportMapper.findBySession(sessionId);
        if (report == null) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }
        return report;
    }

    private double calculateWeightedScore(DimensionAggregate aggregate) {
        double score = 0.0;
        for (EvaluationDimension dim : EvaluationDimension.values()) {
            DimensionAggregate.DimensionScore ds = aggregate.get(dim);
            if (ds != null) {
                score += ds.avgScore() * dim.getWeight();
            }
        }
        return score;
    }

    private String buildConversationSummary(Long sessionId) {
        List<InterviewRoundEntity> rounds = roundService.listBySession(sessionId);
        StringBuilder sb = new StringBuilder();
        for (InterviewRoundEntity round : rounds) {
            if (round.getAnswer() != null && !round.getAnswer().isBlank()) {
                sb.append("Q").append(round.getSeq()).append(": ")
                        .append(truncate(round.getQuestion(), 200))
                        .append("\nA: ")
                        .append(truncate(round.getAnswer(), 500))
                        .append("\n\n");
            }
        }
        return sb.toString();
    }

    private String buildResumeSummary(ResumeEntity resume) {
        if (resume.getParsedJson() != null && !resume.getParsedJson().isBlank()) {
            return resume.getParsedJson();
        }
        String rawText = resume.getRawText();
        if (rawText == null || rawText.isBlank()) {
            return "未提供";
        }
        return rawText.length() > RESUME_SUMMARY_MAX
                ? rawText.substring(0, RESUME_SUMMARY_MAX)
                : rawText;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
