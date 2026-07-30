package com.aims.gateway.controller.interview;

import com.aims.agent.InterviewPlanGenerator;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.Result;
import com.aims.core.common.exception.BizException;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.InterviewSessionStore;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.QuestionRagService;
import com.aims.infra.persistence.service.ResumeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 面试会话 REST API。 */
@RestController
@RequestMapping("/api/v1/interviews")
@Tag(name = "面试会话")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    /** 简历摘要最大长度。 */
    private static final int RESUME_SUMMARY_MAX = 2000;

    /** RAG 检索 Top-K。 */
    private static final int RAG_TOP_K = 10;

    /** 默认预计面试时长（分钟）。 */
    private static final int ESTIMATED_MINUTES = 30;

    private final InterviewSessionService sessionService;
    private final InterviewRoundService roundService;
    private final PositionService positionService;
    private final ResumeService resumeService;
    private final QuestionRagService questionRagService;
    private final InterviewPlanGenerator planGenerator;
    private final InterviewSessionStore sessionStore;
    private final EvaluationMessageProducer evaluationMessageProducer;
    private final ObjectMapper objectMapper;

    public InterviewController(
            InterviewSessionService sessionService,
            InterviewRoundService roundService,
            PositionService positionService,
            ResumeService resumeService,
            QuestionRagService questionRagService,
            InterviewPlanGenerator planGenerator,
            InterviewSessionStore sessionStore,
            EvaluationMessageProducer evaluationMessageProducer,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.positionService = positionService;
        this.resumeService = resumeService;
        this.questionRagService = questionRagService;
        this.planGenerator = planGenerator;
        this.sessionStore = sessionStore;
        this.evaluationMessageProducer = evaluationMessageProducer;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "分页查询面试会话列表")
    @GetMapping("")
    public Result<IPage<InterviewResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        IPage<InterviewSessionEntity> result = sessionService.page(new Page<>(page, size), status);
        IPage<InterviewResponse> mapped = result.convert(InterviewResponse::from);
        return Result.ok(mapped);
    }

    @Operation(summary = "创建面试会话", description = "创建面试会话，状态默认为 CREATED")
    @PostMapping("")
    public Result<InterviewResponse> create(@Valid @RequestBody CreateInterviewRequest req) {
        InterviewSessionEntity entity = sessionService.create(req.candidateId(), req.positionId());
        return Result.ok(InterviewResponse.from(entity));
    }

    @Operation(summary = "查询面试会话详情", description = "根据 ID 查询面试会话详情")
    @GetMapping("/{id}")
    public Result<InterviewResponse> getById(@PathVariable Long id) {
        InterviewSessionEntity entity = sessionService.getById(id);
        return Result.ok(InterviewResponse.from(entity));
    }

    @Operation(summary = "生成面试计划并开始", description = "生成面试计划，进入 IN_PROGRESS 状态；失败时状态置为 FAILED")
    @PostMapping("/{id}/start")
    public Result<InterviewResponse> start(@PathVariable Long id) {
        // 1. 查会话
        InterviewSessionEntity session = sessionService.getById(id);
        // 2. 校验状态为 CREATED
        SessionStatus current = SessionStatus.valueOf(session.getStatus());
        if (current != SessionStatus.CREATED) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        try {
            // 3. 进入 PLANNING
            sessionService.updateStatus(id, SessionStatus.PLANNING);
            // 4. 查岗位
            PositionEntity position = positionService.getById(session.getPositionId());
            // 5. 查简历
            ResumeEntity resume = resumeService.getById(session.getCandidateId());
            // 6. RAG 检索
            List<QuestionSearchResult> ragResults =
                    questionRagService.search(position.getJdText(), RAG_TOP_K);
            String ragQuestions = formatRagQuestions(ragResults);
            // 7. 生成面试计划
            InterviewPlan plan =
                    planGenerator.generate(
                            resume.getCandidateName(),
                            position.getTitle(),
                            position.getJdText(),
                            buildResumeSummary(resume),
                            ragQuestions,
                            ESTIMATED_MINUTES);
            // 8. 序列化并保存计划
            String planJson = objectMapper.writeValueAsString(plan);
            sessionService.savePlan(id, planJson);
            // 9. 进入 IN_PROGRESS 并标记开始
            sessionService.updateStatus(id, SessionStatus.IN_PROGRESS);
            sessionService.markStarted(id);
            // 10. 返回最新会话
            InterviewSessionEntity updated = sessionService.getById(id);
            return Result.ok(InterviewResponse.from(updated));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成面试计划失败 sessionId={}", id, e);
            safeFail(id);
            throw new BizException(ErrorCode.SESSION_PLAN_FAILED, e.getMessage());
        }
    }

    @Operation(summary = "结束面试", description = "将会话状态置为 EVALUATING，触发 Kafka 异步评估流程")
    @PostMapping("/{id}/finish")
    public Result<InterviewResponse> finish(@PathVariable Long id) {
        InterviewSessionEntity session = sessionService.getById(id);
        SessionStatus current = SessionStatus.valueOf(session.getStatus());
        if (current != SessionStatus.IN_PROGRESS && current != SessionStatus.PAUSED) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        // 释放连接锁
        sessionStore.forceUnlock(id);
        // 状态转为 EVALUATING
        sessionService.updateStatus(id, SessionStatus.EVALUATING);
        sessionService.updateEvaluationStatus(id, "PENDING");
        // 发送 Kafka 评估请求
        evaluationMessageProducer.sendEvaluationRequest(id);
        InterviewSessionEntity updated = sessionService.getById(id);
        return Result.ok(InterviewResponse.from(updated));
    }

    @Operation(summary = "暂停面试", description = "将会话状态置为 PAUSED")
    @PostMapping("/{id}/pause")
    public Result<InterviewResponse> pause(@PathVariable Long id) {
        sessionService.updateStatus(id, SessionStatus.PAUSED);
        InterviewSessionEntity entity = sessionService.getById(id);
        return Result.ok(InterviewResponse.from(entity));
    }

    @Operation(summary = "取消面试", description = "将会话状态置为 CANCELLED 并标记结束时间")
    @PostMapping("/{id}/cancel")
    public Result<InterviewResponse> cancel(@PathVariable Long id) {
        sessionService.updateStatus(id, SessionStatus.CANCELLED);
        sessionService.markEnded(id);
        InterviewSessionEntity entity = sessionService.getById(id);
        return Result.ok(InterviewResponse.from(entity));
    }

    @Operation(summary = "恢复面试", description = "从 PAUSED 状态恢复为 IN_PROGRESS")
    @PostMapping("/{id}/resume")
    public Result<InterviewResponse> resume(@PathVariable Long id) {
        InterviewSessionEntity session = sessionService.getById(id);
        SessionStatus current = SessionStatus.valueOf(session.getStatus());
        if (current != SessionStatus.PAUSED) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        sessionService.updateStatus(id, SessionStatus.IN_PROGRESS);
        InterviewSessionEntity entity = sessionService.getById(id);
        return Result.ok(InterviewResponse.from(entity));
    }

    @Operation(summary = "查询面试轮次列表", description = "按 seq 排序返回所有轮次，用于面试间重连恢复历史消息")
    @GetMapping("/{id}/rounds")
    public Result<List<RoundResponse>> listRounds(@PathVariable Long id) {
        // 校验会话存在
        sessionService.getById(id);
        List<InterviewRoundEntity> rounds = roundService.listBySession(id);
        List<RoundResponse> response =
                rounds.stream().map(RoundResponse::from).collect(Collectors.toList());
        return Result.ok(response);
    }

    @Operation(summary = "删除面试会话", description = "级联删除轮次数据，不可恢复")
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sessionService.delete(id);
        return Result.ok(null);
    }

    // ---- 私有辅助方法 ----

    /** 构建简历摘要：优先使用 parsedJson，否则使用 rawText 截断。 */
    private String buildResumeSummary(ResumeEntity resume) {
        if (resume.getParsedJson() != null && !resume.getParsedJson().isBlank()) {
            return resume.getParsedJson();
        }
        String rawText = resume.getRawText();
        if (rawText == null || rawText.isBlank()) {
            return "未提供";
        }
        if (rawText.length() > RESUME_SUMMARY_MAX) {
            return rawText.substring(0, RESUME_SUMMARY_MAX);
        }
        return rawText;
    }

    /** 格式化 RAG 检索结果为文本。 */
    private String formatRagQuestions(List<QuestionSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "无相关题库参考";
        }
        return results.stream()
                .map(r -> "- [" + r.category() + "/" + r.difficulty() + "] " + r.content())
                .collect(Collectors.joining("\n"));
    }

    /** 安全地将会话置为 FAILED（忽略二次异常）。 */
    private void safeFail(Long id) {
        try {
            sessionService.updateStatus(id, SessionStatus.FAILED);
        } catch (Exception ex) {
            log.warn("将会话置为 FAILED 失败 sessionId={}", id, ex);
        }
    }
}
