package com.aims.agent.orchestration.state;

import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.QaPair;
import com.aims.core.session.SessionStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从持久化数据构建 {@link InterviewState} 初始状态。
 *
 * <p>用于：新面试启动 / 断点续面重建状态。调用方（gateway/infra 层）负责从实体类映射到 {@link RoundInitData} 和原始参数，避免
 * interview-agent → interview-infra 循环依赖。
 *
 * @since 1.1.0
 */
public class InterviewStateFactory {

    /**
     * 从会话元数据和轮次列表构建初始状态。
     *
     * @param sessionId 会话 ID
     * @param persona 面试官人设字符串（对应 {@link InterviewerPersona} 枚举名）
     * @param status 会话状态字符串（对应 {@link SessionStatus} 枚举名）
     * @param candidateName 候选人姓名
     * @param positionTitle 岗位名称
     * @param jdText JD 文本
     * @param resumeSummary 简历摘要
     * @param plan 面试计划（可为 null，表示尚未生成）
     * @param rounds 已有轮次列表（可为空，表示新面试）
     * @return 初始化后的 InterviewState
     */
    public InterviewState create(
            Long sessionId,
            String persona,
            String status,
            String candidateName,
            String positionTitle,
            String jdText,
            String resumeSummary,
            InterviewPlan plan,
            List<RoundInitData> rounds) {

        Map<String, Object> data = new HashMap<>();

        // 会话元数据
        data.put(InterviewState.SESSION_ID, sessionId);
        data.put(InterviewState.CANDIDATE_NAME, candidateName != null ? candidateName : "");
        data.put(InterviewState.POSITION_TITLE, positionTitle != null ? positionTitle : "");
        data.put(InterviewState.JD_TEXT, jdText != null ? jdText : "");
        data.put(InterviewState.RESUME_SUMMARY, resumeSummary != null ? resumeSummary : "");
        data.put(InterviewState.PERSONA, InterviewerPersona.fromString(persona));
        data.put(
                InterviewState.SESSION_STATUS,
                status != null ? SessionStatus.valueOf(status) : SessionStatus.CREATED);

        // 面试计划
        if (plan != null) {
            data.put(InterviewState.INTERVIEW_PLAN, plan);
            data.put(InterviewState.TOTAL_ROUNDS, plan.questions().size());
        }

        // 对话历史 — 从已有轮次重建
        List<QaPair> qaHistory = new ArrayList<>();
        List<String> questionsAsked = new ArrayList<>();
        if (rounds != null) {
            for (RoundInitData round : rounds) {
                questionsAsked.add(round.question());
                if (round.answer() != null && !round.answer().isBlank()) {
                    qaHistory.add(new QaPair(round.seq(), round.question(), round.answer()));
                }
            }
        }
        data.put(InterviewState.QA_HISTORY, qaHistory);
        data.put(InterviewState.QUESTIONS_ASKED, questionsAsked);

        // 当前轮次 — 取最后一个未回答的轮次
        RoundInitData currentRound = findCurrentRound(rounds);
        if (currentRound != null) {
            data.put(InterviewState.CURRENT_ROUND_ID, currentRound.id());
            data.put(InterviewState.CURRENT_SEQ, currentRound.seq());
            data.put(InterviewState.CURRENT_QUESTION, currentRound.question());
            data.put(
                    InterviewState.CURRENT_ANSWER,
                    currentRound.answer() != null ? currentRound.answer() : "");
            data.put(InterviewState.PARENT_SEQ, currentRound.parentSeq());
            data.put(InterviewState.FOLLOW_UP_INDEX, currentRound.followUpIndex());
            data.put(
                    InterviewState.FOLLOW_UP_TYPE,
                    currentRound.followUpType() != null
                            ? FollowUpType.valueOf(currentRound.followUpType())
                            : FollowUpType.NONE);
        }

        return new InterviewState(data);
    }

    /** 查找最后一个未回答的轮次（与 handleAnswer 逻辑一致）。 */
    private RoundInitData findCurrentRound(List<RoundInitData> rounds) {
        if (rounds == null || rounds.isEmpty()) {
            return null;
        }
        for (int i = rounds.size() - 1; i >= 0; i--) {
            RoundInitData r = rounds.get(i);
            if (r.answer() == null || r.answer().isBlank()) {
                return r;
            }
        }
        return null;
    }
}
