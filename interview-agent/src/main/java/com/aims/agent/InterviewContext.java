package com.aims.agent;

import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import java.util.List;

/**
 * 面试上下文，传递给 {@link InterviewerAgent}。
 *
 * @param sessionId 面试会话 ID
 * @param candidateName 候选人姓名
 * @param positionTitle 岗位名称
 * @param plan 面试计划
 * @param currentRound 当前轮次序号（从 1 开始）
 * @param recentQuestions 最近已问的问题
 * @param recentAnswers 最近已收到的回答
 * @param resumeFacts 简历事实摘要
 * @param ragQuestions RAG 检索到的参考题目
 * @param persona 面试官人设
 * @param runningSummary 早期轮次的滚动摘要（前 5 轮为 null，之后每 5 轮更新）
 * @param tighten 总指挥判定 TIGHTEN 时 true：本轮提问应收敛话题、勿深挖
 */
public record InterviewContext(
        Long sessionId,
        String candidateName,
        String positionTitle,
        InterviewPlan plan,
        int currentRound,
        List<String> recentQuestions,
        List<String> recentAnswers,
        String resumeFacts,
        String ragQuestions,
        InterviewerPersona persona,
        String runningSummary,
        boolean tighten) {

    public int totalRounds() {
        return plan != null && plan.questions() != null ? plan.questions().size() : 0;
    }
}
