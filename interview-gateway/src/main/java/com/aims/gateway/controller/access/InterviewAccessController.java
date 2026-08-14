package com.aims.gateway.controller.access;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.Result;
import com.aims.core.common.exception.BizException;
import com.aims.core.session.SessionStatus;
import com.aims.gateway.controller.interview.RoundResponse;
import com.aims.gateway.controller.report.EvaluationResponse;
import com.aims.gateway.controller.report.ReportResponse;
import com.aims.gateway.security.GuestTokenService;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.ReportEntity;
import com.aims.infra.persistence.service.EvaluationService;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ReportService;
import com.aims.infra.persistence.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 候选人免登录入口。
 *
 * <p>info / verify 为公开接口；会话/轮次/报告只读接口需携带 guestToken（role=GUEST，绑定 sessionId）。
 */
@RestController
@RequestMapping("/api/v1/access/interviews")
@Tag(name = "候选人免登录入口")
public class InterviewAccessController {

    /** 密码校验失败次数上限（1 小时窗口）。 */
    private static final int MAX_VERIFY_FAILURES = 10;

    private static final Duration FAIL_WINDOW = Duration.ofHours(1);
    private static final String FAIL_KEY_PREFIX = "interview:access:fail:";

    private final InterviewSessionService sessionService;
    private final ResumeService resumeService;
    private final PositionService positionService;
    private final InterviewRoundService roundService;
    private final ReportService reportService;
    private final EvaluationService evaluationService;
    private final GuestTokenService guestTokenService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    public InterviewAccessController(
            InterviewSessionService sessionService,
            ResumeService resumeService,
            PositionService positionService,
            InterviewRoundService roundService,
            ReportService reportService,
            EvaluationService evaluationService,
            GuestTokenService guestTokenService,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redis) {
        this.sessionService = sessionService;
        this.resumeService = resumeService;
        this.positionService = positionService;
        this.roundService = roundService;
        this.reportService = reportService;
        this.evaluationService = evaluationService;
        this.guestTokenService = guestTokenService;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    @Operation(summary = "查询候选人入口信息", description = "公开接口，无需登录；返回候选人名/岗位/状态等非敏感信息")
    @GetMapping("/{accessToken}/info")
    public Result<AccessInfoResponse> info(@PathVariable String accessToken) {
        InterviewSessionEntity session = sessionService.getByAccessToken(accessToken);
        return Result.ok(
                new AccessInfoResponse(
                        session.getId(),
                        resolveCandidateName(session),
                        resolvePosition(session),
                        session.getStatus(),
                        session.getAccessPassword() != null,
                        true));
    }

    @Operation(summary = "校验访问密码并签发 guestToken", description = "公开接口，无需登录；密码正确后返回候选人短期凭证")
    @PostMapping("/{accessToken}/verify")
    public Result<VerifyResponse> verify(
            @PathVariable String accessToken, @RequestBody VerifyRequest req) {
        InterviewSessionEntity session = sessionService.getByAccessToken(accessToken);

        // 失败限流（1 小时窗口）
        String failKey = FAIL_KEY_PREFIX + accessToken;
        Long failures = redis.opsForValue().increment(failKey);
        if (failures != null && failures == 1) {
            redis.expire(failKey, FAIL_WINDOW);
        }
        if (failures != null && failures > MAX_VERIFY_FAILURES) {
            throw new BizException(ErrorCode.ACCESS_RATE_LIMITED);
        }

        String storedHash = session.getAccessPassword();
        String raw = req.password() == null ? "" : req.password();
        boolean ok = storedHash == null || passwordEncoder.matches(raw, storedHash);
        if (!ok) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "访问密码错误");
        }
        redis.delete(failKey);

        String guestToken = guestTokenService.issueGuestToken(session.getId());
        return Result.ok(new VerifyResponse(session.getId(), guestToken));
    }

    @Operation(summary = "候选会话视图", description = "携带 guestToken 访问，仅返回候选人自身会话")
    @PreAuthorize("hasRole('GUEST')")
    @GetMapping("/{sessionId}")
    public Result<GuestSessionResponse> session(@PathVariable Long sessionId) {
        requireSameSession(sessionId);
        InterviewSessionEntity session = sessionService.getById(sessionId);
        return Result.ok(GuestSessionResponse.from(session));
    }

    @Operation(summary = "候选历史轮次", description = "携带 guestToken 访问，用于重连恢复历史消息")
    @PreAuthorize("hasRole('GUEST')")
    @GetMapping("/{sessionId}/rounds")
    public Result<List<RoundResponse>> rounds(@PathVariable Long sessionId) {
        requireSameSession(sessionId);
        sessionService.getById(sessionId);
        return Result.ok(
                roundService.listBySession(sessionId).stream().map(RoundResponse::from).toList());
    }

    @Operation(summary = "候选查看自己的报告", description = "携带 guestToken 访问，仅返回自身会话报告")
    @PreAuthorize("hasRole('GUEST')")
    @GetMapping("/{sessionId}/report")
    public Result<ReportResponse> report(@PathVariable Long sessionId) {
        requireSameSession(sessionId);
        ReportEntity report = reportService.getBySession(sessionId);
        InterviewSessionEntity session = sessionService.getById(sessionId);
        return Result.ok(ReportResponse.from(report, session.getTotalScore()));
    }

    @Operation(
            summary = "候选恢复自己的面试",
            description = "携带 guestToken 访问，仅恢复自身会话（PAUSED → IN_PROGRESS）")
    @PreAuthorize("hasRole('GUEST')")
    @PostMapping("/{sessionId}/resume")
    public Result<Void> resume(@PathVariable Long sessionId) {
        requireSameSession(sessionId);
        InterviewSessionEntity session = sessionService.getById(sessionId);
        if (SessionStatus.valueOf(session.getStatus()) != SessionStatus.PAUSED) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        sessionService.updateStatus(sessionId, SessionStatus.IN_PROGRESS);
        return Result.ok(null);
    }

    @Operation(summary = "候选开始面试", description = "CANDIDATE_ONLY 模式下候选人确认开始，PLANNING → IN_PROGRESS")
    @PreAuthorize("hasRole('GUEST')")
    @PostMapping("/{sessionId}/start")
    public Result<GuestSessionResponse> start(@PathVariable Long sessionId) {
        requireSameSession(sessionId);
        InterviewSessionEntity session = sessionService.getById(sessionId);
        if (!"CANDIDATE_ONLY".equals(session.getAccessMode())) {
            throw new BizException(ErrorCode.ACCESS_DENIED, "该会话未开放候选端入口");
        }
        if (SessionStatus.valueOf(session.getStatus()) != SessionStatus.PLANNING) {
            throw new BizException(ErrorCode.SESSION_STATUS_CONFLICT);
        }
        sessionService.updateStatus(sessionId, SessionStatus.IN_PROGRESS);
        sessionService.markStarted(sessionId);
        return Result.ok(GuestSessionResponse.from(sessionService.getById(sessionId)));
    }

    @Operation(summary = "候选查看自己的评分明细", description = "携带 guestToken 访问，仅返回自身会话评分")
    @PreAuthorize("hasRole('GUEST')")
    @GetMapping("/{sessionId}/evaluations")
    public Result<List<EvaluationResponse>> evaluations(@PathVariable Long sessionId) {
        requireSameSession(sessionId);
        sessionService.getById(sessionId);
        return Result.ok(
                evaluationService.listBySession(sessionId).stream()
                        .map(EvaluationResponse::from)
                        .toList());
    }

    /** 校验 guestToken 绑定的 sid 与路径 sessionId 一致，防越权访问他人会话。 */
    private void requireSameSession(Long sessionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object details = auth == null ? null : auth.getDetails();
        Long sid = details instanceof Number n ? n.longValue() : null;
        if (sid == null || !sid.equals(sessionId)) {
            throw new BizException(ErrorCode.ACCESS_DENIED, "无权访问该面试");
        }
    }

    private String resolveCandidateName(InterviewSessionEntity session) {
        Long resumeId =
                session.getResumeId() != null ? session.getResumeId() : session.getCandidateId();
        if (resumeId == null) {
            return "候选人";
        }
        try {
            return resumeService.getById(resumeId).getCandidateName();
        } catch (Exception e) {
            return "候选人";
        }
    }

    private String resolvePosition(InterviewSessionEntity session) {
        if (session.getPositionId() == null) {
            return "通用岗位";
        }
        try {
            return positionService.getById(session.getPositionId()).getTitle();
        } catch (Exception e) {
            return "通用岗位";
        }
    }

    /** 入口信息响应。 */
    public record AccessInfoResponse(
            Long sessionId,
            String candidateName,
            String position,
            String status,
            boolean requirePassword,
            boolean enabled) {}

    /** 密码校验请求。 */
    public record VerifyRequest(String password) {}

    /** 校验通过响应。 */
    public record VerifyResponse(Long sessionId, String guestToken) {}

    /** 候选会话视图（仅暴露候选人所需字段）。 */
    public record GuestSessionResponse(
            Long id,
            String status,
            String persona,
            String planJson,
            String startedAt,
            String endedAt) {

        static GuestSessionResponse from(InterviewSessionEntity e) {
            return new GuestSessionResponse(
                    e.getId(),
                    e.getStatus(),
                    e.getPersona(),
                    e.getPlanJson(),
                    e.getStartedAt() == null ? null : e.getStartedAt().toString(),
                    e.getEndedAt() == null ? null : e.getEndedAt().toString());
        }
    }
}
