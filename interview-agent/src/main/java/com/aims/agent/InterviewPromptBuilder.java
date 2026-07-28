package com.aims.agent;

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

    private static final String PLAN_SYSTEM =
            """
            你是面试计划设计专家。请根据岗位 JD、候选人简历和题库事实，生成结构化面试计划。
            要求：
            1. 只能基于提供的岗位、简历和题库事实生成计划，不编造候选人经历或公司信息
            2. 题目数必须在 8~10 题之间
            3. 每道题必须标记 topic、difficulty、evaluationFocus
            4. 计划模块题目数之和必须等于计划题目数
            5. 只输出符合 InterviewPlan schema 的 JSON，不要额外说明
            """;

    private InterviewPromptBuilder() {}

    // ---- 面试官提问 ----

    /** 面试官系统 Prompt。 */
    public static String interviewerSystem() {
        return INTERVIEWER_SYSTEM;
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

    /** 面试计划生成系统 Prompt。 */
    public static String planSystem() {
        return PLAN_SYSTEM;
    }

    /**
     * 构造面试计划生成用户 Prompt。
     *
     * @param candidateName 候选人姓名
     * @param positionTitle 岗位名称
     * @param jdText 岗位 JD 原文
     * @param resumeSummary 简历摘要
     * @param ragQuestions RAG 检索到的参考题目文本
     * @return 渲染后的用户 Prompt
     */
    public static String planUser(
            String candidateName,
            String positionTitle,
            String jdText,
            String resumeSummary,
            String ragQuestions) {
        return """
               岗位名称：%s
               岗位 JD：%s
               候选人姓名：%s
               简历摘要：%s
               题库参考题目：
               %s

               请生成面试计划 JSON，字段说明：
               - candidateName: 候选人姓名
               - position: 岗位名称
               - sections: 面试模块列表，每个含 name/questionCount/objective
               - questions: 题目列表，每个含 questionId/topic/difficulty/followUpHints/evaluationFocus
               - estimatedMinutes: 预计面试时长（分钟）
               - version: 计划版本号，如 "1.0"
               """
                .formatted(
                        safe(positionTitle),
                        safe(jdText),
                        safe(candidateName),
                        safe(resumeSummary),
                        safe(ragQuestions));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未提供" : value;
    }
}
