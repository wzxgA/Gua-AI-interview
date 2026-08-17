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
import com.aims.infra.persistence.entity.ProctorEventEntity;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.messaging.EvaluationMessageProducer;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.InterviewSessionStore;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ProctorEventService;
import com.aims.infra.persistence.service.QuestionRagService;
import com.aims.infra.persistence.service.ResumeService;
import com.aims.infra.persistence.service.ResumeSummaryBuilder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    /** RAG 检索 Top-K。 */
    private static final int RAG_TOP_K = 10;

    /** 默认题数。 */
    private static final int DEFAULT_QUESTION_COUNT = 10;

    /** 默认难度偏好。 */
    private static final String DEFAULT_DIFFICULTY = "BALANCED";

    /** 难度对应每题时长（分钟）。 */
    private static final java.util.Map<String, Integer> MINUTES_PER_QUESTION =
            java.util.Map.of("BASIC", 2, "BALANCED", 3, "ADVANCED", 5);

    /** 系统生成访问密码长度与字符集（去掉易混淆字符）。 */
    private static final int ACCESS_PASSWORD_LENGTH = 8;

    private static final String ACCESS_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final SecureRandom secureRandom = new SecureRandom();

    private final InterviewSessionService sessionService;
    private final InterviewRoundService roundService;
    private final PositionService positionService;
    private final ResumeService resumeService;
    private final ResumeSummaryBuilder resumeSummaryBuilder;
    private final QuestionRagService questionRagService;
    private final InterviewPlanGenerator planGenerator;
    private final InterviewSessionStore sessionStore;
    private final EvaluationMessageProducer evaluationMessageProducer;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final ProctorEventService proctorEventService;

    public InterviewController(
            InterviewSessionService sessionService,
            InterviewRoundService roundService,
            PositionService positionService,
            ResumeService resumeService,
            ResumeSummaryBuilder resumeSummaryBuilder,
            QuestionRagService questionRagService,
            InterviewPlanGenerator planGenerator,
            InterviewSessionStore sessionStore,
            EvaluationMessageProducer evaluationMessageProducer,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder,
            ProctorEventService proctorEventService) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.positionService = positionService;
        this.resumeService = resumeService;
        this.resumeSummaryBuilder = resumeSummaryBuilder;
        this.questionRagService = questionRagService;
        this.planGenerator = planGenerator;
        this.sessionStore = sessionStore;
        this.evaluationMessageProducer = evaluationMessageProducer;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.proctorEventService = proctorEventService;
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

    @Operation(summary = "创建面试会话", description = "创建面试会话，状态默认为 CREATED（不自动生成候选人链接）")
    @PostMapping("")
    public Result<InterviewResponse> create(@Valid @RequestBody CreateInterviewRequest req) {
        InterviewSessionEntity entity =
                sessionService.create(req.candidateId(), req.positionId(), req.persona());
        return Result.ok(InterviewResponse.from(entity));
    }

    @Operation(summary = "获取候选人访问配置", description = "返回面试链接令牌、入口开关、是否有密码与入口模式")
    @GetMapping("/{id}/access")
    public Result<InterviewAccessResponse> getAccess(@PathVariable Long id) {
        InterviewSessionEntity entity = sessionService.getById(id);
        return Result.ok(
                new InterviewAccessResponse(
                        entity.getAccessToken(),
                        entity.getAccessEnabled(),
                        entity.getAccessPassword() != null,
                        null,
                        entity.getAccessMode(),
                        ProctorConfig.from(entity)));
    }

    @Operation(
            summary = "生成候选人面试链接",
            description = "生成访问令牌与密码，设为 CANDIDATE_ONLY 模式（仅 PLANNING/PAUSED 允许）")
    @PostMapping("/{id}/access/generate")
    public Result<InterviewAccessResponse> generateAccess(
            @PathVariable Long id, @RequestBody(required = false) ResetAccessPasswordRequest req) {
        InterviewSessionEntity entity = sessionService.getById(id);
        SessionStatus current = SessionStatus.valueOf(entity.getStatus());
        if (current != SessionStatus.PLANNING && current != SessionStatus.PAUSED) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT, "请先生成面试计划后再生成候选人面试链接");
        }
        String token = sessionService.ensureAccessToken(id);
        String raw =
                (req == null || req.password() == null || req.password().isBlank())
                        ? generateAccessPassword()
                        : req.password();
        sessionService.updateAccessPassword(id, passwordEncoder.encode(raw));
        sessionService.updateAccessMode(id, "CANDIDATE_ONLY");
        // 可选：开启防作弊检测（生成链接时选择，重置密码不影响已配置项）
        if (req != null && req.proctor() != null) {
            sessionService.saveProctor(id, req.proctor().toJson());
        }
        InterviewSessionEntity updated = sessionService.getById(id);
        return Result.ok(
                new InterviewAccessResponse(
                        updated.getAccessToken(),
                        true,
                        true,
                        raw,
                        "CANDIDATE_ONLY",
                        ProctorConfig.from(updated)));
    }

    @Operation(summary = "查询防作弊事件", description = "增量查询：仅返回 id 大于 after 的事件（控制台实时面板轮询用）")
    @GetMapping("/{id}/proctor/events")
    public Result<List<ProctorEventResponse>> proctorEvents(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "50") int limit) {
        sessionService.getById(id);
        List<ProctorEventEntity> events = proctorEventService.listAfter(id, after, limit);
        return Result.ok(
                events.stream()
                        .map(
                                e ->
                                        new ProctorEventResponse(
                                                e.getId(),
                                                e.getEventType(),
                                                e.getOccurredAt() == null
                                                        ? null
                                                        : e.getOccurredAt().toString(),
                                                e.getDurationMs(),
                                                e.getDetail()))
                        .toList());
    }

    @Operation(summary = "防作弊事件摘要", description = "按类型聚合事件数与总时长（控制台摘要卡片）")
    @GetMapping("/{id}/proctor/summary")
    public Result<ProctorSummaryResponse> proctorSummary(@PathVariable Long id) {
        sessionService.getById(id);
        List<ProctorSummaryResponse.ProctorTypeSummary> items =
                proctorEventService.countByType(id).stream()
                        .map(
                                r ->
                                        new ProctorSummaryResponse.ProctorTypeSummary(
                                                String.valueOf(r.get("type")),
                                                ((Number) r.get("cnt")).longValue(),
                                                ((Number) r.get("total_duration_ms")).longValue()))
                        .toList();
        return Result.ok(new ProctorSummaryResponse(items));
    }

    @Operation(summary = "设置/重置候选人访问密码", description = "重新生成候选人访问密码（bcrypt 存储），返回新密码明文")
    @PostMapping("/{id}/access/password")
    public Result<InterviewAccessResponse> resetAccessPassword(
            @PathVariable Long id, @RequestBody ResetAccessPasswordRequest req) {
        String raw = req.password();
        if (raw == null || raw.isBlank()) {
            raw = generateAccessPassword();
        }
        sessionService.updateAccessPassword(id, passwordEncoder.encode(raw));
        InterviewSessionEntity entity = sessionService.getById(id);
        return Result.ok(
                new InterviewAccessResponse(
                        entity.getAccessToken(),
                        entity.getAccessEnabled(),
                        true,
                        raw,
                        entity.getAccessMode(),
                        ProctorConfig.from(entity)));
    }

    @Operation(summary = "作废候选人入口", description = "关闭候选人链接访问权限并恢复管理端面试能力")
    @PostMapping("/{id}/access/disable")
    public Result<InterviewAccessResponse> disableAccess(@PathVariable Long id) {
        sessionService.disableAccess(id);
        InterviewSessionEntity entity = sessionService.getById(id);
        return Result.ok(
                new InterviewAccessResponse(
                        entity.getAccessToken(),
                        false,
                        entity.getAccessPassword() != null,
                        null,
                        "DISABLED",
                        ProctorConfig.from(entity)));
    }

    @Operation(summary = "查询面试会话详情", description = "根据 ID 查询面试会话详情")
    @GetMapping("/{id}")
    public Result<InterviewResponse> getById(@PathVariable Long id) {
        InterviewSessionEntity entity = sessionService.getById(id);
        return Result.ok(InterviewResponse.from(entity));
    }

    @Operation(summary = "生成面试计划", description = "生成面试计划，进入 PLANNING 状态；失败时状态置为 FAILED")
    @PostMapping("/{id}/plan")
    public Result<InterviewResponse> plan(
            @PathVariable Long id, @RequestBody(required = false) StartPlanRequest req) {
        // 解析参数（可选，默认值兼容旧逻辑）
        int questionCount = DEFAULT_QUESTION_COUNT;
        String difficulty = DEFAULT_DIFFICULTY;
        if (req != null) {
            if (req.questionCount() != null
                    && req.questionCount() >= 1
                    && req.questionCount() <= 30) {
                questionCount = req.questionCount();
            }
            if (req.difficulty() != null && !req.difficulty().isBlank()) {
                difficulty = req.difficulty().toUpperCase();
            }
        }
        int minutesPerQuestion = MINUTES_PER_QUESTION.getOrDefault(difficulty, 3);
        int estimatedMinutes = questionCount * minutesPerQuestion;

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
                    questionRagService.search(position.getJdText(), RAG_TOP_K).results();
            String ragQuestions = formatRagQuestions(ragResults);
            // 7. 生成面试计划
            InterviewPlan plan =
                    planGenerator.generate(
                            resume.getCandidateName(),
                            position.getTitle(),
                            position.getJdText(),
                            resumeSummaryBuilder.build(resume),
                            ragQuestions,
                            questionCount,
                            difficulty,
                            estimatedMinutes);
            // 8. 序列化并保存计划，保持在 PLANNING 状态等待用户确认
            String planJson = objectMapper.writeValueAsString(plan);
            sessionService.savePlan(id, planJson);
            // 9. 返回最新会话（状态为 PLANNING）
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

    @Operation(summary = "开始面试", description = "从 PLANNING 状态进入 IN_PROGRESS，标记开始时间")
    @PostMapping("/{id}/start")
    public Result<InterviewResponse> start(@PathVariable Long id) {
        InterviewSessionEntity session = sessionService.getById(id);
        SessionStatus current = SessionStatus.valueOf(session.getStatus());
        if (current != SessionStatus.PLANNING) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        if ("CANDIDATE_ONLY".equals(session.getAccessMode())) {
            throw new BizException(ErrorCode.ACCESS_DENIED, "该面试已设为候选端面试，请通过候选人链接进行");
        }
        sessionService.updateStatus(id, SessionStatus.IN_PROGRESS);
        sessionService.markStarted(id);
        InterviewSessionEntity updated = sessionService.getById(id);
        return Result.ok(InterviewResponse.from(updated));
    }

    @Operation(summary = "结束面试", description = "将会话状态置为 EVALUATING，触发 Kafka 异步评估流程")
    @PostMapping("/{id}/finish")
    public Result<InterviewResponse> finish(@PathVariable Long id) {
        InterviewSessionEntity session = sessionService.getById(id);
        SessionStatus current = SessionStatus.valueOf(session.getStatus());
        if (current != SessionStatus.IN_PROGRESS && current != SessionStatus.PAUSED) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        // 原子条件状态转移：仅当当前状态为 IN_PROGRESS 或 PAUSED 时才转为 EVALUATING
        boolean transitioned =
                sessionService.tryTransitionTo(
                        id,
                        SessionStatus.EVALUATING,
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED);
        if (transitioned) {
            sessionStore.forceUnlock(id);
            sessionService.updateEvaluationStatus(id, "PENDING");
            evaluationMessageProducer.sendEvaluationRequest(id);
        } else {
            log.info("评估已触发，跳过重复请求 sessionId={}", id);
        }
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

    /** 生成 8 位候选访问密码（字母+数字，去除易混淆字符）。 */
    private String generateAccessPassword() {
        StringBuilder sb = new StringBuilder(ACCESS_PASSWORD_LENGTH);
        for (int i = 0; i < ACCESS_PASSWORD_LENGTH; i++) {
            sb.append(
                    ACCESS_PASSWORD_CHARS.charAt(
                            secureRandom.nextInt(ACCESS_PASSWORD_CHARS.length())));
        }
        return sb.toString();
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
