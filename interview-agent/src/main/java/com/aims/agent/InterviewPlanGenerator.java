package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.InterviewPlan;
import org.springframework.stereotype.Service;

/**
 * 面试计划生成器：基于岗位 JD、简历摘要和题库 RAG 结果，调用 {@link AiChatFacade#callForEntity} 生成结构化 {@link InterviewPlan}。
 *
 * <p>Prompt 构建委托给 {@link InterviewPromptBuilder}，本类只负责 AI 调用和结果校验。 使用 STANDARD 档位模型，失败由 {@code
 * AiChatFacade} 内部重试和 {@link ModelRouter} 降级处理。
 */
@Service
public class InterviewPlanGenerator {

    private final AiChatFacade aiChatFacade;

    public InterviewPlanGenerator(AiChatFacade aiChatFacade) {
        this.aiChatFacade = aiChatFacade;
    }

    /**
     * 生成面试计划。
     *
     * @param candidateName 候选人姓名
     * @param positionTitle 岗位名称
     * @param jdText 岗位 JD 原文
     * @param resumeSummary 简历摘要
     * @param ragQuestions RAG 检索到的参考题目文本
     * @param questionCount 题目数量
     * @param difficulty 难度偏好（BASIC/BALANCED/ADVANCED）
     * @param estimatedMinutes 预计面试时长（分钟）
     * @return 结构化面试计划
     */
    public InterviewPlan generate(
            String candidateName,
            String positionTitle,
            String jdText,
            String resumeSummary,
            String ragQuestions,
            int questionCount,
            String difficulty,
            int estimatedMinutes) {
        return aiChatFacade.callForEntity(
                ModelTier.STANDARD,
                InterviewPromptBuilder.planSystem(questionCount, difficulty),
                InterviewPromptBuilder.planUser(
                        candidateName,
                        positionTitle,
                        jdText,
                        resumeSummary,
                        ragQuestions,
                        estimatedMinutes),
                InterviewPlan.class);
    }
}
