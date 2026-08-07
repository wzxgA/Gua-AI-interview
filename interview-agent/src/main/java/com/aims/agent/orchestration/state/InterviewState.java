package com.aims.agent.orchestration.state;

import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.QaPair;
import com.aims.core.report.ReportResult;
import com.aims.core.session.SessionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

/**
 * 面试编排状态容器：基于 LangGraph4j AgentState，定义所有面试流程状态字段。
 *
 * <p>使用 Channel + Reducer 模式管理状态更新语义：
 *
 * <ul>
 *   <li>{@code base}（Replace）：新值覆盖旧值，用于当前轮次、决策结果等"当前态"字段
 *   <li>{@code appender}（Append）：新值追加到 List，用于对话历史、评分结果等"累积"字段
 * </ul>
 *
 * <p>所有字段 Key 为 {@code public static final String}，Accessor 使用泛型 {@code value()} 方法消除强制转换。
 *
 * @since 1.1.0
 */
public class InterviewState extends AgentState {

    // ===== Key 常量：会话元数据（Replace）=====
    public static final String SESSION_ID = "sessionId";
    public static final String CANDIDATE_NAME = "candidateName";
    public static final String POSITION_TITLE = "positionTitle";
    public static final String JD_TEXT = "jdText";
    public static final String RESUME_SUMMARY = "resumeSummary";
    public static final String PERSONA = "persona";
    public static final String SESSION_STATUS = "sessionStatus";

    // ===== Key 常量：面试计划（Replace）=====
    public static final String INTERVIEW_PLAN = "interviewPlan";
    public static final String TOTAL_ROUNDS = "totalRounds";

    // ===== Key 常量：对话历史（Append）=====
    public static final String QA_HISTORY = "qaHistory";
    public static final String QUESTIONS_ASKED = "questionsAsked";

    // ===== Key 常量：当前轮次（Replace）=====
    public static final String CURRENT_ROUND_ID = "currentRoundId";
    public static final String CURRENT_SEQ = "currentSeq";
    public static final String CURRENT_QUESTION = "currentQuestion";
    public static final String CURRENT_ANSWER = "currentAnswer";
    public static final String PARENT_SEQ = "parentSeq";
    public static final String FOLLOW_UP_INDEX = "followUpIndex";
    public static final String FOLLOW_UP_TYPE = "followUpType";

    // ===== Key 常量：追问决策（Replace）=====
    public static final String FOLLOW_UP_DECISION = "followUpDecision";
    public static final String FOLLOW_UP_COUNT = "followUpCount";

    // ===== Key 常量：评估结果（Append）=====
    public static final String ROUND_EVALUATIONS = "roundEvaluations";
    public static final String EVALUATED_ROUND_IDS = "evaluatedRoundIds";

    // ===== Key 常量：摘要与报告（Replace）=====
    public static final String RUNNING_SUMMARY = "runningSummary";
    public static final String LAST_SUMMARIZED_SEQ = "lastSummarizedSeq";
    public static final String REPORT_RESULT = "reportResult";

    // ===== Key 常量：错误与重试（Replace）=====
    public static final String LAST_ERROR = "lastError";
    public static final String RETRY_COUNT = "retryCount";

    // ===== Key 常量：RAG 检索（Replace）=====
    public static final String RAG_QUESTIONS = "ragQuestions";

    /** SCHEMA：为每个字段指定 Channel 策略（Replace / Append）。 */
    public static final Map<String, Channel<?>> SCHEMA =
            Map.ofEntries(
                    // 会话元数据 — Replace
                    Map.entry(SESSION_ID, Channels.base(() -> null)),
                    Map.entry(CANDIDATE_NAME, Channels.base(() -> "")),
                    Map.entry(POSITION_TITLE, Channels.base(() -> "")),
                    Map.entry(JD_TEXT, Channels.base(() -> "")),
                    Map.entry(RESUME_SUMMARY, Channels.base(() -> "")),
                    Map.entry(PERSONA, Channels.base(() -> InterviewerPersona.FRIENDLY)),
                    Map.entry(SESSION_STATUS, Channels.base(() -> SessionStatus.CREATED)),

                    // 面试计划 — Replace
                    Map.entry(INTERVIEW_PLAN, Channels.base(() -> null)),
                    Map.entry(TOTAL_ROUNDS, Channels.base(() -> 0)),

                    // 对话历史 — Append
                    Map.entry(QA_HISTORY, Channels.appender(ArrayList::new)),
                    Map.entry(QUESTIONS_ASKED, Channels.appender(ArrayList::new)),

                    // 当前轮次 — Replace
                    Map.entry(CURRENT_ROUND_ID, Channels.base(() -> null)),
                    Map.entry(CURRENT_SEQ, Channels.base(() -> 0)),
                    Map.entry(CURRENT_QUESTION, Channels.base(() -> "")),
                    Map.entry(CURRENT_ANSWER, Channels.base(() -> "")),
                    Map.entry(PARENT_SEQ, Channels.base(() -> null)),
                    Map.entry(FOLLOW_UP_INDEX, Channels.base(() -> null)),
                    Map.entry(FOLLOW_UP_TYPE, Channels.base(() -> FollowUpType.NONE)),

                    // 追问决策 — Replace
                    Map.entry(FOLLOW_UP_DECISION, Channels.base(() -> null)),
                    Map.entry(FOLLOW_UP_COUNT, Channels.base(() -> 0)),

                    // 评估结果 — Append
                    Map.entry(ROUND_EVALUATIONS, Channels.appender(ArrayList::new)),
                    Map.entry(EVALUATED_ROUND_IDS, Channels.appender(ArrayList::new)),

                    // 摘要与报告 — Replace
                    Map.entry(RUNNING_SUMMARY, Channels.base(() -> null)),
                    Map.entry(LAST_SUMMARIZED_SEQ, Channels.base(() -> 0)),
                    Map.entry(REPORT_RESULT, Channels.base(() -> null)),

                    // 错误与重试 — Replace
                    Map.entry(LAST_ERROR, Channels.base(() -> null)),
                    Map.entry(RETRY_COUNT, Channels.base(() -> 0)),

                    // RAG 检索 — Replace
                    Map.entry(RAG_QUESTIONS, Channels.base(() -> null)));

    // ===== 构造函数 =====
    public InterviewState(Map<String, Object> initData) {
        super(initData);
    }

    // ===== Accessor：会话元数据 =====
    public Long sessionId() {
        return this.<Long>value(SESSION_ID).orElse(null);
    }

    public String candidateName() {
        return this.<String>value(CANDIDATE_NAME).orElse("");
    }

    public String positionTitle() {
        return this.<String>value(POSITION_TITLE).orElse("");
    }

    public String jdText() {
        return this.<String>value(JD_TEXT).orElse("");
    }

    public String resumeSummary() {
        return this.<String>value(RESUME_SUMMARY).orElse("");
    }

    public InterviewerPersona persona() {
        return this.<InterviewerPersona>value(PERSONA).orElse(InterviewerPersona.FRIENDLY);
    }

    public SessionStatus sessionStatus() {
        return this.<SessionStatus>value(SESSION_STATUS).orElse(SessionStatus.CREATED);
    }

    // ===== Accessor：面试计划 =====
    public InterviewPlan interviewPlan() {
        return this.<InterviewPlan>value(INTERVIEW_PLAN).orElse(null);
    }

    public int totalRounds() {
        return this.<Integer>value(TOTAL_ROUNDS).orElse(0);
    }

    // ===== Accessor：对话历史 =====
    @SuppressWarnings("unchecked")
    public List<QaPair> qaHistory() {
        return this.<List<QaPair>>value(QA_HISTORY).orElse(new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public List<String> questionsAsked() {
        return this.<List<String>>value(QUESTIONS_ASKED).orElse(new ArrayList<>());
    }

    // ===== Accessor：当前轮次 =====
    public Long currentRoundId() {
        return this.<Long>value(CURRENT_ROUND_ID).orElse(null);
    }

    public int currentSeq() {
        return this.<Integer>value(CURRENT_SEQ).orElse(0);
    }

    public String currentQuestion() {
        return this.<String>value(CURRENT_QUESTION).orElse("");
    }

    public String currentAnswer() {
        return this.<String>value(CURRENT_ANSWER).orElse("");
    }

    public Integer parentSeq() {
        return this.<Integer>value(PARENT_SEQ).orElse(null);
    }

    public Integer followUpIndex() {
        return this.<Integer>value(FOLLOW_UP_INDEX).orElse(null);
    }

    public FollowUpType followUpType() {
        return this.<FollowUpType>value(FOLLOW_UP_TYPE).orElse(FollowUpType.NONE);
    }

    // ===== Accessor：追问决策 =====
    public FollowUpDecision followUpDecision() {
        return this.<FollowUpDecision>value(FOLLOW_UP_DECISION).orElse(null);
    }

    public int followUpCount() {
        return this.<Integer>value(FOLLOW_UP_COUNT).orElse(0);
    }

    // ===== Accessor：评估结果 =====
    @SuppressWarnings("unchecked")
    public List<RoundEvaluation> roundEvaluations() {
        return this.<List<RoundEvaluation>>value(ROUND_EVALUATIONS).orElse(new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public List<Long> evaluatedRoundIds() {
        return this.<List<Long>>value(EVALUATED_ROUND_IDS).orElse(new ArrayList<>());
    }

    // ===== Accessor：摘要与报告 =====
    public String runningSummary() {
        return this.<String>value(RUNNING_SUMMARY).orElse(null);
    }

    public int lastSummarizedSeq() {
        return this.<Integer>value(LAST_SUMMARIZED_SEQ).orElse(0);
    }

    public ReportResult reportResult() {
        return this.<ReportResult>value(REPORT_RESULT).orElse(null);
    }

    // ===== Accessor：错误与重试 =====
    public String lastError() {
        return this.<String>value(LAST_ERROR).orElse(null);
    }

    public int retryCount() {
        return this.<Integer>value(RETRY_COUNT).orElse(0);
    }

    // ===== Accessor：RAG 检索 =====
    public String ragQuestions() {
        return this.<String>value(RAG_QUESTIONS).orElse(null);
    }

    // ===== 便捷方法 =====

    /** 已回答轮次数（含追问） */
    public int answeredCount() {
        return qaHistory().size();
    }

    /** 当前是否为主问题（非追问） */
    public boolean isMainQuestion() {
        return parentSeq() == null;
    }

    /** 是否已达到题目上限 */
    public boolean reachedRoundLimit() {
        int total = totalRounds();
        return total > 0 && answeredCount() >= total;
    }

    @Override
    public String toString() {
        return "InterviewState{sessionId="
                + sessionId()
                + ", status="
                + sessionStatus()
                + ", seq="
                + currentSeq()
                + ", answered="
                + answeredCount()
                + "/"
                + totalRounds()
                + ", followUps="
                + followUpCount()
                + ", evaluations="
                + roundEvaluations().size()
                + ", hasReport="
                + (reportResult() != null)
                + '}';
    }
}
