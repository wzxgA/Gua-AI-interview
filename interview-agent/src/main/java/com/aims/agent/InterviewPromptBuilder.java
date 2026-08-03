package com.aims.agent;

import com.aims.core.interview.InterviewerPersona;

/**
 * 面试 Prompt 统一构建器：集中管理面试官提问和计划生成的 Prompt 模板。
 *
 * <p>提取此类的目的：
 *
 * <ul>
 *   <li>Agent / Generator 只负责调用 AI，不承担 Prompt 拼接职责
 *   <li>Prompt 构建可独立单测，无需构造完整 Context
 *   <li>未来替换 Prompt 模板或多语言时只改此类
 * </ul>
 */
public final class InterviewPromptBuilder {

    private static final String INTERVIEWER_SYSTEM =
            """
            你是一名资深面试官。请严格根据岗位 JD、候选人简历、面试计划和题库事实提问。
            要求：
            1. 不要编造简历中不存在的经历
            2. 不要评分
            3. 不要一次提出多个问题
            4. 只输出下一道面试问题，不要输出标题、分析过程或 JSON
            5. 语气专业、友好，引导候选人充分展示
            """;

    private static final String PLAN_SYSTEM_TEMPLATE =
            """
            你是面试计划设计专家。请根据岗位 JD、候选人简历和题库事实，生成结构化面试计划。
            要求：
            1. 只能基于提供的岗位、简历和题库事实生成计划，不编造候选人经历或公司信息
            2. 题目数必须为 %d 题
            3. 难度偏好（控制 EASY/MEDIUM/HARD 的分布比例）：%s
            4. 每道题必须标记 topic、difficulty（EASY/MEDIUM/HARD）、evaluationFocus
            5. 计划模块题目数之和必须等于计划题目数
            6. 只输出符合 InterviewPlan schema 的 JSON，不要额外说明
            """;

    private InterviewPromptBuilder() {}

    // ---- 人设提示词 ----

    /** 各人设的系统提示词前缀（追加到基础面试官 Prompt 之后）。 */
    private static final java.util.Map<InterviewerPersona, String> PERSONA_PROMPTS =
            java.util.Map.of(
                    InterviewerPersona.FRIENDLY,
                    """
                    面试风格：温和型
                    - 语气亲和友好，鼓励候选人充分展示
                    - 候选人回答后先肯定合理部分，再深入追问
                    - 给予候选人充分的思考时间，不要催促
                    - 适当引导，帮助紧张的候选人放松
                    """,
                    InterviewerPersona.PRESSURE,
                    """
                    面试风格：压力面型
                    - 语气严肃直接，模拟高压面试场景
                    - 候选人回答不充分时直接指出不足
                    - 追问时施加压力，质疑回答中的漏洞
                    - 适当制造紧迫感，考察抗压能力
                    - 注意：保持专业，不要人身攻击或侮辱
                    """,
                    InterviewerPersona.TECHNICAL,
                    """
                    面试风格：深度技术型
                    - 聚焦技术原理与底层实现
                    - 要求候选人给出具体方案设计、架构图思路
                    - 追问边界条件、异常处理、性能权衡
                    - 考察技术选型的理由与 trade-off
                    - 适当考察系统设计能力
                    """);

    // ---- 面试官提问 ----

    /** 面试官系统 Prompt（默认温和型）。 */
    public static String interviewerSystem() {
        return interviewerSystem(InterviewerPersona.FRIENDLY);
    }

    /** 面试官系统 Prompt（带人设）。 */
    public static String interviewerSystem(InterviewerPersona persona) {
        String personaPrompt = PERSONA_PROMPTS.getOrDefault(persona, "");
        return personaPrompt.isBlank()
                ? INTERVIEWER_SYSTEM
                : INTERVIEWER_SYSTEM + "\n" + personaPrompt;
    }

    /**
     * 构造面试官提问用户 Prompt。
     *
     * @param context 面试上下文
     * @return 渲染后的用户 Prompt
     */
    public static String interviewerUser(InterviewContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前是第 ")
                .append(context.currentRound())
                .append(" 题，共 ")
                .append(context.totalRounds())
                .append(" 题。\n");
        sb.append("岗位：").append(safe(context.positionTitle())).append('\n');
        sb.append("候选人：").append(safe(context.candidateName())).append('\n');

        if (context.plan() != null
                && context.plan().questions() != null
                && context.currentRound() <= context.plan().questions().size()) {
            var q = context.plan().questions().get(context.currentRound() - 1);
            sb.append("计划题目主题：")
                    .append(q.topic())
                    .append("，难度：")
                    .append(q.difficulty())
                    .append("，考察重点：")
                    .append(q.evaluationFocus())
                    .append('\n');
        }

        var recentQuestions =
                context.recentQuestions() == null
                        ? java.util.List.<String>of()
                        : context.recentQuestions();
        var recentAnswers =
                context.recentAnswers() == null
                        ? java.util.List.<String>of()
                        : context.recentAnswers();
        int recentSize = Math.min(recentQuestions.size(), recentAnswers.size());
        if (recentSize > 0) {
            sb.append("最近对话：\n");
            for (int i = 0; i < recentSize; i++) {
                sb.append("问题：")
                        .append(recentQuestions.get(i))
                        .append("\n回答：")
                        .append(recentAnswers.get(i))
                        .append('\n');
            }
        }

        if (context.resumeFacts() != null && !context.resumeFacts().isBlank()) {
            sb.append("候选人简历事实：").append(context.resumeFacts()).append('\n');
        }
        if (context.ragQuestions() != null && !context.ragQuestions().isBlank()) {
            sb.append("题库参考：").append(context.ragQuestions());
        }
        return sb.toString();
    }

    // ---- 面试计划生成 ----

    /** 难度偏好描述映射 */
    private static final java.util.Map<String, String> DIFFICULTY_DESC =
            java.util.Map.of(
                    "BASIC", "以 EASY 为主（约60% EASY、30% MEDIUM、10% HARD）",
                    "BALANCED", "均衡搭配（约20% EASY、50% MEDIUM、30% HARD）",
                    "ADVANCED", "以 HARD 为主（约10% EASY、20% MEDIUM、70% HARD）");

    /**
     * 面试计划生成系统 Prompt。
     *
     * @param questionCount 题目数量
     * @param difficulty 难度偏好（BASIC/BALANCED/ADVANCED）
     * @return 渲染后的系统 Prompt
     */
    public static String planSystem(int questionCount, String difficulty) {
        String desc = DIFFICULTY_DESC.getOrDefault(difficulty, DIFFICULTY_DESC.get("BALANCED"));
        return PLAN_SYSTEM_TEMPLATE.formatted(questionCount, desc);
    }

    /**
     * 构造面试计划生成用户 Prompt。
     *
     * @param candidateName 候选人姓名
     * @param positionTitle 岗位名称
     * @param jdText 岗位 JD 原文
     * @param resumeSummary 简历摘要
     * @param ragQuestions RAG 检索到的参考题目文本
     * @param estimatedMinutes 预计面试时长（分钟）
     * @return 渲染后的用户 Prompt
     */
    public static String planUser(
            String candidateName,
            String positionTitle,
            String jdText,
            String resumeSummary,
            String ragQuestions,
            int estimatedMinutes) {
        return """
岗位名称：%s
岗位 JD：%s
候选人姓名：%s
简历摘要：%s
题库参考题目：
%s
预计面试时长：%d 分钟

请生成面试计划 JSON，字段说明：
- candidateName: 候选人姓名
- position: 岗位名称
- sections: 面试模块列表，每个含 name/questionCount/objective
- questions: 题目列表，每个含 questionId/topic/difficulty(EASY/MEDIUM/HARD)/followUpHints/evaluationFocus
- estimatedMinutes: 预计面试时长（分钟）
- version: 计划版本号，如 "1.0"
"""
                .formatted(
                        safe(positionTitle),
                        safe(jdText),
                        safe(candidateName),
                        safe(resumeSummary),
                        safe(ragQuestions),
                        estimatedMinutes);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未提供" : value;
    }
}
