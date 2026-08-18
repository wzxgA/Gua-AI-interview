package com.aims.gateway.controller.report;

import com.aims.core.common.Result;
import com.aims.infra.persistence.entity.EvaluationEntity;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.ReportEntity;
import com.aims.infra.persistence.service.EvaluationService;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 评估报告 REST API。 */
@RestController
@RequestMapping("/api/v1/interviews")
@Tag(name = "评估报告")
public class ReportController {

    private final ReportService reportService;
    private final EvaluationService evaluationService;
    private final InterviewSessionService sessionService;
    private final InterviewRoundService roundService;

    public ReportController(
            ReportService reportService,
            EvaluationService evaluationService,
            InterviewSessionService sessionService,
            InterviewRoundService roundService) {
        this.reportService = reportService;
        this.evaluationService = evaluationService;
        this.sessionService = sessionService;
        this.roundService = roundService;
    }

    @Operation(summary = "获取面试报告", description = "获取面试报告（含综合评述、维度聚合、录用建议、综合得分、矛盾点清单）")
    @GetMapping("/{id}/report")
    public Result<ReportResponse> getReport(@PathVariable Long id) {
        ReportEntity report = reportService.getBySession(id);
        InterviewSessionEntity session = sessionService.getById(id);
        List<InterviewRoundEntity> rounds = roundService.listBySession(id);
        return Result.ok(ReportResponse.from(report, session.getTotalScore(), rounds));
    }

    @Operation(summary = "获取所有轮次评分明细", description = "获取会话所有轮次的五维度评分明细")
    @GetMapping("/{id}/evaluations")
    public Result<List<EvaluationResponse>> getEvaluations(@PathVariable Long id) {
        // 校验会话存在
        sessionService.getById(id);
        List<EvaluationEntity> evaluations = evaluationService.listBySession(id);
        return Result.ok(evaluations.stream().map(EvaluationResponse::from).toList());
    }

    @Operation(summary = "获取指定轮次评分明细", description = "获取指定轮次的五维度评分明细")
    @GetMapping("/{id}/evaluations/{roundId}")
    public Result<List<EvaluationResponse>> getRoundEvaluations(
            @PathVariable Long id, @PathVariable Long roundId) {
        // 校验会话存在
        sessionService.getById(id);
        List<EvaluationEntity> evaluations = evaluationService.listByRound(roundId);
        return Result.ok(evaluations.stream().map(EvaluationResponse::from).toList());
    }
}
