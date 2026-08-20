package com.aims.infra.persistence.service.impl;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.core.dashboard.DashboardStats;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.mapper.EvaluationMapper;
import com.aims.infra.persistence.mapper.InterviewRoundMapper;
import com.aims.infra.persistence.mapper.InterviewSessionMapper;
import com.aims.infra.persistence.mapper.ProctorEventMapper;
import com.aims.infra.persistence.mapper.ReportMapper;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final EvaluationMapper evaluationMapper;
    private final ProctorEventMapper proctorEventMapper;
    private final ReportMapper reportMapper;

    public InterviewSessionServiceImpl(
            InterviewSessionMapper sessionMapper,
            InterviewRoundMapper roundMapper,
            EvaluationMapper evaluationMapper,
            ProctorEventMapper proctorEventMapper,
            ReportMapper reportMapper) {
        this.sessionMapper = sessionMapper;
        this.roundMapper = roundMapper;
        this.evaluationMapper = evaluationMapper;
        this.proctorEventMapper = proctorEventMapper;
        this.reportMapper = reportMapper;
    }

    @Override
    @Transactional
    public InterviewSessionEntity create(
            Long candidateId, Long resumeId, Long positionId, String persona) {
        if (resumeId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "简历 ID 不能为空");
        }
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setCandidateId(candidateId);
        entity.setResumeId(resumeId);
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
    public void saveProctor(Long id, String proctorJson) {
        getById(id);
        sessionMapper.updateProctorJson(id, proctorJson);
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
    public void recordFinish(Long id, String finishedBy, String finishReason) {
        InterviewSessionEntity entity = getById(id);
        entity.setFinishedBy(finishedBy);
        entity.setFinishReason(finishReason);
        entity.setUpdatedAt(Instant.now());
        sessionMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void updateTtsEnabled(Long id, Boolean ttsEnabled) {
        InterviewSessionEntity entity = getById(id);
        entity.setTtsEnabled(Boolean.TRUE.equals(ttsEnabled));
        entity.setUpdatedAt(Instant.now());
        sessionMapper.updateById(entity);
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
        // 级联删除关联数据，顺序需满足外键依赖（先删子表，再删轮次/会话）
        // interview_proctor_event / interview_evaluation / interview_report → session
        // interview_evaluation.round_id → interview_round
        proctorEventMapper.deleteBySession(id);
        evaluationMapper.deleteBySession(id);
        reportMapper.deleteBySession(id);
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

    /** 仪表盘展示用业务时区（东八区），用于按日聚合边界。 */
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Shanghai");

    /** 得分五档区间（顺序即展示顺序）。 */
    private static final List<String> SCORE_RANGES = List.of("0-1", "1-2", "2-3", "3-4", "4-5");

    @Override
    public DashboardStats getDashboardStats() {
        List<DashboardStats.StatusCount> statusCounts = buildStatusCounts();
        List<DashboardStats.TrendPoint> dailyTrend = buildDailyTrend();
        DashboardStats.ScoreStats scoreStats = buildScoreStats(null, null);
        List<DashboardStats.RecentInterview> recentInterviews = buildRecentInterviews();
        return new DashboardStats(statusCounts, dailyTrend, scoreStats, recentInterviews);
    }

    @Override
    public DashboardStats.ScoreStats getScoreStats(
            Integer days, LocalDate startDate, LocalDate endDate) {
        OffsetDateTime[] range = resolveScoreRange(days, startDate, endDate);
        return buildScoreStats(range[0], range[1]);
    }

    @Override
    public List<DashboardStats.ScorePoint> getScorePoints(
            Integer days, LocalDate startDate, LocalDate endDate) {
        OffsetDateTime[] range = resolveScoreRange(days, startDate, endDate);
        List<DashboardStats.ScorePoint> points = new ArrayList<>();
        for (Map<String, Object> row : sessionMapper.scorePointsOfScored(range[0], range[1])) {
            Object score = row.get("total_score");
            points.add(
                    new DashboardStats.ScorePoint(
                            score == null ? 0d : toDouble(score),
                            toInstant(row.get("created_at"))));
        }
        return points;
    }

    /** 解析得分统计/散点图的时间过滤区间：[start, end)，任一为 null 表示不限制。 */
    private OffsetDateTime[] resolveScoreRange(
            Integer days, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return new OffsetDateTime[] {
                startDate.atStartOfDay(DASHBOARD_ZONE).toOffsetDateTime(),
                endDate.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toOffsetDateTime()
            };
        }
        if (days != null && days > 0) {
            OffsetDateTime now = OffsetDateTime.now(DASHBOARD_ZONE);
            return new OffsetDateTime[] {now.minusDays(days), now};
        }
        return new OffsetDateTime[] {null, null};
    }

    /** 状态分布：按枚举顺序补齐缺失状态为 0，保证前端图表顺序稳定。 */
    private List<DashboardStats.StatusCount> buildStatusCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : sessionMapper.countGroupByStatus()) {
            counts.put(String.valueOf(row.get("status")), toInt(row.get("count")));
        }
        List<DashboardStats.StatusCount> result = new ArrayList<>();
        for (SessionStatus status : SessionStatus.values()) {
            result.add(
                    new DashboardStats.StatusCount(
                            status.name(), counts.getOrDefault(status.name(), 0)));
        }
        return result;
    }

    /** 近 30 天趋势：以今天为界回溯 30 天，缺失日期补 0，升序返回。 */
    private List<DashboardStats.TrendPoint> buildDailyTrend() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : sessionMapper.countDailySince30Days()) {
            counts.put(String.valueOf(row.get("date")), toInt(row.get("count")));
        }
        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        List<DashboardStats.TrendPoint> result = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            result.add(new DashboardStats.TrendPoint(date, counts.getOrDefault(date, 0)));
        }
        return result;
    }

    /**
     * 得分统计：平均分 + 五档分布（补齐空档为 0）。
     *
     * <p>start/end 均非 null 时按 created_at 限定时间区间；任一为 null 表示全部时间。
     */
    private DashboardStats.ScoreStats buildScoreStats(OffsetDateTime start, OffsetDateTime end) {
        BigDecimal avg = sessionMapper.avgScoreOfScored(start, end);
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : sessionMapper.countGroupByScoreRange(start, end)) {
            counts.put(String.valueOf(row.get("range")), toInt(row.get("count")));
        }
        List<DashboardStats.ScoreRange> distribution = new ArrayList<>();
        for (String range : SCORE_RANGES) {
            distribution.add(new DashboardStats.ScoreRange(range, counts.getOrDefault(range, 0)));
        }
        return new DashboardStats.ScoreStats(avg == null ? BigDecimal.ZERO : avg, distribution);
    }

    /** 最近面试摘要（最多 8 条）。 */
    private List<DashboardStats.RecentInterview> buildRecentInterviews() {
        List<DashboardStats.RecentInterview> result = new ArrayList<>();
        for (Map<String, Object> row : sessionMapper.selectRecentSessions(8)) {
            Object score = row.get("total_score");
            result.add(
                    new DashboardStats.RecentInterview(
                            toLong(row.get("id")),
                            (String) row.get("candidate_name"),
                            (String) row.get("position_title"),
                            String.valueOf(row.get("status")),
                            score == null ? null : new BigDecimal(score.toString()),
                            toInstant(row.get("created_at"))));
        }
        return result;
    }

    /** Map 值安全转 long（COUNT/BIGSERIAL 可能以 Long/Integer/BigDecimal 返回）。 */
    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return ((Number) value).longValue();
    }

    /** Map 值安全转 int（统计计数用，避免 long 被全局序列化为字符串）。 */
    private static int toInt(Object value) {
        return (int) toLong(value);
    }

    /** Map 值安全转 double（percentile_cont 返回 double，MIN/MAX 聚合返回 BigDecimal）。 */
    private static double toDouble(Object value) {
        if (value == null) {
            return 0d;
        }
        return ((Number) value).doubleValue();
    }

    /** 时间戳兼容转换：驱动可能返回 Timestamp/OffsetDateTime/Instant/LocalDateTime。 */
    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(DASHBOARD_ZONE).toInstant();
        }
        return Instant.parse(value.toString());
    }
}
