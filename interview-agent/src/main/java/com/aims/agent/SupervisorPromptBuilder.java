package com.aims.agent;

import com.aims.core.interview.SupervisorContext;

/** 总指挥提示词构建。 */
public final class SupervisorPromptBuilder {

    private SupervisorPromptBuilder() {}

    public static String system() {
        return """
你是面试总指挥。任务：根据面试实时状态（进度/耗时/回答质量）判断节奏，不参与出题与提问。

输出必须严格为以下 JSON，不要输出任何其他内容：
{"action":"CONTINUE|TIGHTEN|END","reason":"一句话理由","suggestedRemaining":null,"hardStop":false}

判定规则：
- CONTINUE：节奏正常，按计划继续。
- TIGHTEN：进度偏慢或回答质量下降，建议收敛当前话题（不要深挖）。
  典型：已耗时超过计划 80% 但已完成主问题数不足计划的 60%；或近期回答质量连续偏低。
- END：超时/进度严重异常，建议提前结束。
  典型：已耗时超过计划 120%。
- hardStop：仅当超时极其严重时才为 true。

限制：
- 一期不允许自动减题：suggestedRemaining 仅作参考建议，不要擅自建议大改题量。
- 不要臆造不存在的状态数据。
""";
    }

    public static String user(SupervisorContext ctx) {
        Double avg = ctx.avgScore();
        return """
会话 ID：%s
计划题数（主问题）：%d
已完成主问题数：%d
当前题序（正在回答）：%d
已答轮次（含追问）：%d（含追问，可能超过计划题数，属正常现象；进度判定以"已完成主问题数/计划题数"为准）
当前题追问次数：%d
已耗时：%d 毫秒（约 %.1f 分钟）
回答质量均值：%s

请给出节奏建议 JSON。
"""
                .formatted(
                        ctx.sessionId(),
                        ctx.totalRounds(),
                        ctx.answeredMainCount(),
                        ctx.currentSeq(),
                        ctx.answeredCount(),
                        ctx.followUpCount(),
                        ctx.elapsedMs(),
                        ctx.elapsedMs() / 60000.0,
                        avg != null ? String.format("%.2f", avg) : "暂无评估（null）");
    }
}
