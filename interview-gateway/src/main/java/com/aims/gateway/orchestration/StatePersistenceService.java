package com.aims.gateway.orchestration;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.QaPair;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ResumeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * State 持久化服务：{@link InterviewState} 与 DB 双向同步。
 *
 * <p>Phase 5 引入：{@code InterviewWorkflowEngine} 在 Graph 执行前后调用本服务，确保 State 与 DB 一致。
 *
 * <p>核心方法：
 *
 * <ul>
 *   <li>{@link #buildInitialState(Long)} — DB → State，面试开始时构建初始 InterviewState
 *   <li>{@link #syncFromState(Long, InterviewState)} — State → DB，Graph 执行后将变更同步
 *   <li>{@link #rebuildFromDb(Long)} — DB → State，断线恢复时从 DB 重建完整 State
 * </ul>
 *
 * <p>幂等性：{@link #syncFromState} 对 QA_HISTORY 做幂等处理——已创建的 round 不重复创建，已更新的 answer 不重复更新。
 *
 * @since 1.1.0 Phase 5
 */
@Component
public class StatePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(StatePersistenceService.class);
    private static final int RESUME_SUMMARY_MAX = 2000;

    private final InterviewSessionService sessionService;
    private final InterviewRoundService roundService;
    private final ResumeService resumeService;
    private final PositionService positionService;
    private final ObjectMapper objectMapper;

    public StatePersistenceService(
            InterviewSessionService sessionService,
            InterviewRoundService roundService,
            ResumeService resumeService,
            PositionService positionService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.resumeService = resumeService;
        this.positionService = positionService;
        this.objectMapper = objectMapper;
    }

    /**
     * DB → State：面试开始时构建初始 InterviewState。
     *
     * <p>从 SessionEntity 加载 plan/resume/position，组装 State 所有元数据字段，currentSeq=0。
     *
     * @param sessionId 面试 sessionId
     * @return 初始 InterviewState
     */
    public InterviewState buildInitialState(Long sessionId) {
        InterviewSessionEntity entity = sessionService.getById(sessionId);
        if (entity == null) {
            throw new IllegalArgumentException("Session 不存在: " + sessionId);
        }

        ResumeEntity resume = resumeService.getById(entity.getCandidateId());
        PositionEntity position = positionService.getById(entity.getPositionId());
        InterviewPlan plan = parsePlan(entity.getPlanJson());

        Map<String, Object> data = new HashMap<>();
        data.put(InterviewState.SESSION_ID, entity.getId());
        data.put(InterviewState.CANDIDATE_NAME, resume != null ? resume.getCandidateName() : "");
        data.put(InterviewState.POSITION_TITLE, position != null ? position.getTitle() : "");
        data.put(InterviewState.JD_TEXT, position != null ? position.getJdText() : "");
        data.put(InterviewState.RESUME_SUMMARY, buildResumeSummary(resume));
        data.put(InterviewState.PERSONA, InterviewerPersona.fromString(entity.getPersona()));
        data.put(InterviewState.TOTAL_ROUNDS, getTotalRounds(plan));
        data.put(InterviewState.CURRENT_SEQ, 0);
        data.put(InterviewState.QA_HISTORY, new ArrayList<QaPair>());
        data.put(InterviewState.FOLLOW_UP_COUNT, 0);
        data.put(InterviewState.ROUND_EVALUATIONS, new ArrayList<>());
        data.put(InterviewState.SESSION_STATUS, SessionStatus.IN_PROGRESS);
        if (plan != null) {
            data.put(InterviewState.INTERVIEW_PLAN, plan);
        }
        return new InterviewState(data);
    }

    /**
     * State → DB：Graph 执行后将 State 变更同步到 DB。
     *
     * <p>同步策略：
     *
     * <ol>
     *   <li>QA_HISTORY → InterviewRoundEntity（幂等：按 seq 判断是否已存在）
     *   <li>SESSION_STATUS → sessionService.updateStatus（仅当状态非初始时）
     *   <li>REPORT_RESULT → 暂不处理（Phase 6 接入 ReportService）
     * </ol>
     *
     * @param sessionId 面试 sessionId
     * @param state Graph 执行后的最新 State
     */
    public void syncFromState(Long sessionId, InterviewState state) {
        // 1. QA_HISTORY → InterviewRoundEntity（幂等）
        List<InterviewRoundEntity> existingRounds = roundService.listBySession(sessionId);
        Map<Integer, InterviewRoundEntity> existingBySeq = new HashMap<>();
        for (InterviewRoundEntity r : existingRounds) {
            if (r.getSeq() != null) {
                existingBySeq.put(r.getSeq(), r);
            }
        }

        for (QaPair qa : state.qaHistory()) {
            InterviewRoundEntity existing = existingBySeq.get(qa.seq());
            if (existing == null) {
                // 新问题：创建 round
                roundService.createRound(sessionId, qa.seq(), qa.question());
                log.debug(
                        "syncFromState 创建轮次 sessionId={} seq={} questionLen={}",
                        sessionId,
                        qa.seq(),
                        qa.question() != null ? qa.question().length() : 0);
            } else if (qa.answer() != null
                    && !qa.answer().isBlank()
                    && (existing.getAnswer() == null || existing.getAnswer().isBlank())) {
                // 已有问题，新回答：更新 answer
                roundService.updateAnswer(existing.getId(), qa.answer());
                log.debug("syncFromState 更新回答 sessionId={} seq={}", sessionId, qa.seq());
            }
        }

        // 2. SESSION_STATUS → DB（仅当非 IN_PROGRESS 初始态时更新）
        SessionStatus status = state.sessionStatus();
        if (status != SessionStatus.IN_PROGRESS && status != SessionStatus.CREATED) {
            sessionService.updateStatus(sessionId, status);
        }
    }

    /**
     * DB → State：断线恢复时从 DB 重建完整 InterviewState。
     *
     * <p>从已持久化的 round 列表重建 QA_HISTORY，从 session 取 plan/persona，计算 currentSeq。
     *
     * @param sessionId 面试 sessionId
     * @return 重建的 InterviewState（若 session 不存在抛 IllegalArgumentException）
     */
    public InterviewState rebuildFromDb(Long sessionId) {
        InterviewSessionEntity entity = sessionService.getById(sessionId);
        if (entity == null) {
            throw new IllegalArgumentException("Session 不存在: " + sessionId);
        }

        ResumeEntity resume = resumeService.getById(entity.getCandidateId());
        PositionEntity position = positionService.getById(entity.getPositionId());
        InterviewPlan plan = parsePlan(entity.getPlanJson());

        // 加载所有轮次，重建 QA_HISTORY
        List<InterviewRoundEntity> rounds = roundService.listBySession(sessionId);
        List<QaPair> qaHistory = new ArrayList<>();
        int currentSeq = 0;
        for (InterviewRoundEntity r : rounds) {
            if (r.getSeq() != null && r.getAnswer() != null && !r.getAnswer().isBlank()) {
                qaHistory.add(new QaPair(r.getSeq(), r.getQuestion(), r.getAnswer()));
                currentSeq = Math.max(currentSeq, r.getSeq());
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put(InterviewState.SESSION_ID, entity.getId());
        data.put(InterviewState.CANDIDATE_NAME, resume != null ? resume.getCandidateName() : "");
        data.put(InterviewState.POSITION_TITLE, position != null ? position.getTitle() : "");
        data.put(InterviewState.JD_TEXT, position != null ? position.getJdText() : "");
        data.put(InterviewState.RESUME_SUMMARY, buildResumeSummary(resume));
        data.put(InterviewState.PERSONA, InterviewerPersona.fromString(entity.getPersona()));
        data.put(InterviewState.TOTAL_ROUNDS, getTotalRounds(plan));
        data.put(InterviewState.CURRENT_SEQ, currentSeq);
        data.put(InterviewState.QA_HISTORY, qaHistory);
        data.put(InterviewState.FOLLOW_UP_COUNT, 0);
        data.put(InterviewState.ROUND_EVALUATIONS, new ArrayList<>());
        data.put(InterviewState.SESSION_STATUS, SessionStatus.IN_PROGRESS);
        if (plan != null) {
            data.put(InterviewState.INTERVIEW_PLAN, plan);
        }
        return new InterviewState(data);
    }

    private InterviewPlan parsePlan(String planJson) {
        if (planJson == null || planJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(planJson, InterviewPlan.class);
        } catch (JsonProcessingException e) {
            log.warn("反序列化面试计划失败 planJsonLen={}", planJson.length(), e);
            return null;
        }
    }

    private int getTotalRounds(InterviewPlan plan) {
        if (plan == null || plan.questions() == null) {
            return 0;
        }
        return plan.questions().size();
    }

    private String buildResumeSummary(ResumeEntity resume) {
        if (resume == null) {
            return "未提供";
        }
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
}
