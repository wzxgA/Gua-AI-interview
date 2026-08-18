package com.aims.gateway.orchestration;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.interview.ConflictDetail;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.QaPair;
import com.aims.core.session.SessionStatus;
import com.aims.infra.persistence.entity.InterviewRoundEntity;
import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.service.ConflictDetailsJson;
import com.aims.infra.persistence.service.InterviewRoundService;
import com.aims.infra.persistence.service.InterviewSessionService;
import com.aims.infra.persistence.service.PositionService;
import com.aims.infra.persistence.service.ResumeService;
import com.aims.infra.persistence.service.ResumeSummaryBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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

    private final InterviewSessionService sessionService;
    private final InterviewRoundService roundService;
    private final ResumeService resumeService;
    private final PositionService positionService;
    private final ResumeSummaryBuilder resumeSummaryBuilder;
    private final ObjectMapper objectMapper;

    public StatePersistenceService(
            InterviewSessionService sessionService,
            InterviewRoundService roundService,
            ResumeService resumeService,
            PositionService positionService,
            ResumeSummaryBuilder resumeSummaryBuilder,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.resumeService = resumeService;
        this.positionService = positionService;
        this.resumeSummaryBuilder = resumeSummaryBuilder;
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

        ResumeEntity resume = resumeService.getById(entity.getResumeId());
        PositionEntity position = positionService.getById(entity.getPositionId());
        InterviewPlan plan = parsePlan(entity.getPlanJson());
        // P5 防御：plan 缺失/解析失败 -> 启动即失败，不进入 0 题评估（totalRounds=0 时 endCheck 恒真）
        if (plan == null || plan.questions() == null || plan.questions().isEmpty()) {
            log.error(
                    "面试计划缺失或解析失败，拒绝启动 sessionId={} planJsonLen={}",
                    sessionId,
                    entity.getPlanJson() != null ? entity.getPlanJson().length() : 0);
            throw new IllegalStateException("面试计划缺失或解析失败，无法启动面试: sessionId=" + sessionId);
        }
        int totalRounds = plan.questions().size();

        Map<String, Object> data = new HashMap<>();
        data.put(InterviewState.SESSION_ID, entity.getId());
        data.put(InterviewState.RESUME_ID, resume != null ? resume.getId() : null);
        data.put(InterviewState.CANDIDATE_NAME, resume != null ? resume.getCandidateName() : "");
        data.put(InterviewState.POSITION_TITLE, position != null ? position.getTitle() : "");
        data.put(InterviewState.JD_TEXT, position != null ? position.getJdText() : "");
        data.put(InterviewState.RESUME_SUMMARY, resumeSummaryBuilder.build(resume));
        data.put(InterviewState.PERSONA, InterviewerPersona.fromString(entity.getPersona()));
        data.put(InterviewState.TOTAL_ROUNDS, totalRounds);
        data.put(InterviewState.CURRENT_SEQ, 0);
        data.put(InterviewState.QA_HISTORY, new ArrayList<QaPair>());
        data.put(InterviewState.FOLLOW_UP_COUNT, 0);
        data.put(InterviewState.ROUND_EVALUATIONS, new ArrayList<>());
        data.put(InterviewState.SESSION_STATUS, SessionStatus.IN_PROGRESS);
        data.put(InterviewState.INTERVIEW_PLAN, plan);
        // F1 总指挥：注入会话开始时间，供 SuperviseNode 计算已耗时
        data.put(
                InterviewState.SESSION_STARTED_AT,
                entity.getStartedAt() != null ? entity.getStartedAt() : Instant.now());
        log.info("构建初始状态 sessionId={} totalRounds={}", sessionId, totalRounds);
        return new InterviewState(data);
    }

    /**
     * State → DB：Graph 执行后将 State 变更同步到 DB。
     *
     * <p>同步策略：
     *
     * <ol>
     *   <li>QA_HISTORY → InterviewRoundEntity（幂等：主问题按 seq、追问按 parentSeq+followUpIndex 判断是否已存在）
     *   <li>暂停窗口待答问题预落库：CURRENT_QUESTION 非空且 CURRENT_ANSWER 为空时预创建轮次（主问题 + 追问）， 保证断线重连能补发当前问题
     *   <li>SESSION_STATUS → sessionService.updateStatus（仅当状态非初始时）
     *   <li>REPORT_RESULT → 暂不处理（Phase 6 接入 ReportService）
     * </ol>
     *
     * @param sessionId 面试 sessionId
     * @param state Graph 执行后的最新 State
     */
    public void syncFromState(Long sessionId, InterviewState state) {
        // 1. 现有轮次索引：主问题按 seq，追问按 parentSeq:followUpIndex
        List<InterviewRoundEntity> existingRounds = roundService.listBySession(sessionId);
        Map<Integer, InterviewRoundEntity> mainBySeq = new HashMap<>();
        Map<String, InterviewRoundEntity> followUpByKey = new HashMap<>();
        for (InterviewRoundEntity r : existingRounds) {
            if (r.getSeq() != null) {
                mainBySeq.put(r.getSeq(), r);
            } else if (r.getParentSeq() != null && r.getFollowUpIndex() != null) {
                followUpByKey.put(r.getParentSeq() + ":" + r.getFollowUpIndex(), r);
            }
        }

        // 2. QA_HISTORY → InterviewRoundEntity（幂等）
        for (QaPair qa : state.qaHistory()) {
            if (qa.followUpIndex() == null) {
                // 主问题：按 seq 幂等
                InterviewRoundEntity existing = mainBySeq.get(qa.seq());
                if (existing == null) {
                    InterviewRoundEntity created =
                            roundService.createRound(sessionId, qa.seq(), qa.question());
                    mainBySeq.put(qa.seq(), created);
                    log.debug(
                            "syncFromState 创建轮次 sessionId={} seq={} questionLen={}",
                            sessionId,
                            qa.seq(),
                            qa.question() != null ? qa.question().length() : 0);
                } else if (qa.answer() != null
                        && !qa.answer().isBlank()
                        && (existing.getAnswer() == null || existing.getAnswer().isBlank())) {
                    roundService.updateAnswer(existing.getId(), qa.answer());
                    log.debug("syncFromState 更新回答 sessionId={} seq={}", sessionId, qa.seq());
                }
            } else {
                // 追问：按 parentSeq:followUpIndex 幂等
                String key = qa.seq() + ":" + qa.followUpIndex();
                InterviewRoundEntity existing = followUpByKey.get(key);
                if (existing == null) {
                    InterviewRoundEntity created =
                            roundService.createRound(
                                    sessionId,
                                    null,
                                    qa.question(),
                                    qa.followUpType() != null ? qa.followUpType().name() : null,
                                    qa.seq(),
                                    qa.followUpIndex());
                    followUpByKey.put(key, created);
                    log.debug(
                            "syncFromState 创建追问轮次 sessionId={} parentSeq={} followUpIndex={}",
                            sessionId,
                            qa.seq(),
                            qa.followUpIndex());
                } else if (qa.answer() != null
                        && !qa.answer().isBlank()
                        && (existing.getAnswer() == null || existing.getAnswer().isBlank())) {
                    roundService.updateAnswer(existing.getId(), qa.answer());
                    log.debug("syncFromState 更新追问回答 sessionId={} key={}", sessionId, key);
                }
            }
        }

        // 3. 暂停窗口预落库：CURRENT_QUESTION 非空且 CURRENT_ANSWER 为空 → 待答问题尚未进入 QA_HISTORY，
        //    预创建轮次保证断线重连补发（主问题 + 追问）
        String pendingQuestion = state.currentQuestion();
        if (pendingQuestion != null
                && !pendingQuestion.isBlank()
                && (state.currentAnswer() == null || state.currentAnswer().isBlank())) {
            if (state.pendingFollowUp()
                    && state.parentSeq() != null
                    && state.followUpIndex() != null) {
                String key = state.parentSeq() + ":" + state.followUpIndex();
                if (!followUpByKey.containsKey(key)) {
                    roundService.createRound(
                            sessionId,
                            null,
                            pendingQuestion,
                            state.followUpType() != null ? state.followUpType().name() : null,
                            state.parentSeq(),
                            state.followUpIndex());
                    log.debug("syncFromState 预创建追问轮次 sessionId={} key={}", sessionId, key);
                }
            } else {
                int seq = state.currentSeq();
                if (!mainBySeq.containsKey(seq)) {
                    roundService.createRound(sessionId, seq, pendingQuestion);
                    log.debug("syncFromState 预创建主问题轮次 sessionId={} seq={}", sessionId, seq);
                }
            }
        }

        // 4. v1.1-F4：矛盾点落库 —— 决策阶段检测到的矛盾点按轮次 key 写入 conflict_details（幂等，已写同值不再更新）
        for (Map.Entry<String, List<ConflictDetail>> entry :
                state.conflictDetailsByRound().entrySet()) {
            String key = entry.getKey();
            InterviewRoundEntity round =
                    key.contains(":")
                            ? followUpByKey.get(key)
                            : mainBySeq.get(Integer.parseInt(key));
            if (round == null) {
                continue;
            }
            if (!ConflictDetailsJson.serialize(entry.getValue())
                    .equals(round.getConflictDetails())) {
                roundService.updateConflictDetails(round.getId(), entry.getValue());
                log.debug(
                        "syncFromState 写入矛盾点 sessionId={} key={} conflicts={}",
                        sessionId,
                        key,
                        entry.getValue().size());
            }
        }

        // 5. SESSION_STATUS → DB（仅当非 IN_PROGRESS 初始态时更新）
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

        ResumeEntity resume = resumeService.getById(entity.getResumeId());
        PositionEntity position = positionService.getById(entity.getPositionId());
        InterviewPlan plan = parsePlan(entity.getPlanJson());

        // 加载所有轮次，重建 QA_HISTORY（主问题 + 追问；追问沿用主问题 seq 并携带 followUpIndex/followUpType）
        List<InterviewRoundEntity> rounds = roundService.listBySession(sessionId);
        List<QaPair> qaHistory = new ArrayList<>();
        Map<String, List<ConflictDetail>> conflictsByRound = new HashMap<>();
        int currentSeq = 0;
        for (InterviewRoundEntity r : rounds) {
            // v1.1-F4：读回矛盾点（key=主问题 seq 或 "seq:followUpIndex"）
            List<ConflictDetail> roundConflicts = ConflictDetailsJson.parse(r.getConflictDetails());
            if (!roundConflicts.isEmpty()) {
                conflictsByRound.put(
                        r.getSeq() != null
                                ? String.valueOf(r.getSeq())
                                : r.getParentSeq() + ":" + r.getFollowUpIndex(),
                        roundConflicts);
            }
            if (r.getAnswer() == null || r.getAnswer().isBlank()) {
                continue;
            }
            if (r.getSeq() != null) {
                qaHistory.add(new QaPair(r.getSeq(), r.getQuestion(), r.getAnswer()));
                currentSeq = Math.max(currentSeq, r.getSeq());
            } else if (r.getParentSeq() != null && r.getFollowUpIndex() != null) {
                qaHistory.add(
                        new QaPair(
                                r.getParentSeq(),
                                r.getQuestion(),
                                r.getAnswer(),
                                r.getFollowUpIndex(),
                                FollowUpType.fromString(r.getFollowUpType())));
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put(InterviewState.SESSION_ID, entity.getId());
        data.put(InterviewState.RESUME_ID, resume != null ? resume.getId() : null);
        data.put(InterviewState.CANDIDATE_NAME, resume != null ? resume.getCandidateName() : "");
        data.put(InterviewState.POSITION_TITLE, position != null ? position.getTitle() : "");
        data.put(InterviewState.JD_TEXT, position != null ? position.getJdText() : "");
        data.put(InterviewState.RESUME_SUMMARY, resumeSummaryBuilder.build(resume));
        data.put(InterviewState.PERSONA, InterviewerPersona.fromString(entity.getPersona()));
        data.put(InterviewState.TOTAL_ROUNDS, getTotalRounds(plan));
        data.put(InterviewState.CURRENT_SEQ, currentSeq);
        data.put(InterviewState.QA_HISTORY, qaHistory);
        data.put(InterviewState.CONFLICT_DETAILS_BY_ROUND, conflictsByRound);
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
}
