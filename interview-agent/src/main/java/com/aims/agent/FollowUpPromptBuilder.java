package com.aims.agent;

import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;

/** 追问 Prompt 构建器。 */
public final class FollowUpPromptBuilder {

    private static final String DECISION_SYSTEM =
            """
你是一位资深面试官助手。你的任务是评估候选人对面试问题的回答质量，决定是否需要追问。

评估维度：
1. 完整性：回答是否覆盖了问题的关键要点
2. 具体性：回答是否有具体的技术细节、项目案例或数据支撑
3. 准确性：回答是否与简历陈述一致，是否存在明显矛盾
4. 相关性：回答是否切题，是否偏题或答非所问

决策类型（输出 action 字段）：
- NEXT：回答充分，无需追问，进入下一题
- CLARIFY：回答模糊，需要澄清具体细节
- DEEPEN：回答浅显，可以深挖技术原理或实现细节
- REDIRECT：回答偏题或疑似背诵，需要引导回正轨

注意：
- 如果候选人回答过于简短（<50字）或纯泛化表述，应追问
- 如果回答与简历存在明显矛盾，应追问（CLARIFY）
- 如果回答提及具体技术但未展开，应追问（DEEPEN）
- 如果回答完整且有具体细节，应进入下一题（NEXT）
- 追问问题应基于预设的追问方向（followUpHints），不脱离面试主线

只输出 JSON，不要额外说明：
{"action":"NEXT|CLARIFY|DEEPEN|REDIRECT","reason":"决策理由（一句话）","followUpQuestion":"追问问题文本（action=NEXT 时为null）"}
""";

    private FollowUpPromptBuilder() {}

    /** 追问决策系统 Prompt。 */
    public static String decisionSystem() {
        return DECISION_SYSTEM;
    }

    /** 构造追问决策用户 Prompt。 */
    public static String decisionUser(FollowUpContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 面试问题\n").append(safe(context.question())).append("\n\n");
        sb.append("## 候选人回答\n").append(safe(context.answer())).append("\n\n");
        sb.append("## 岗位要求\n").append(safe(context.jdText())).append("\n\n");
        sb.append("## 简历摘要\n").append(safe(context.resumeSummary())).append("\n\n");
        if (!context.followUpHints().isEmpty()) {
            sb.append("## 预设追问方向\n");
            for (int i = 0; i < context.followUpHints().size(); i++) {
                sb.append(i + 1).append(". ").append(context.followUpHints().get(i)).append('\n');
            }
        }
        return sb.toString();
    }

    /** 构造追问问题生成用户 Prompt。 */
    public static String followUpUser(FollowUpContext context, FollowUpDecision decision) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在面试候选人 ").append(safe(context.candidateName()));
        sb.append("，应聘岗位 ").append(safe(context.positionTitle())).append("。\n\n");
        sb.append("## 当前问题\n").append(safe(context.question())).append("\n\n");
        sb.append("## 候选人回答\n").append(safe(context.answer())).append("\n\n");
        sb.append("## 追问类型\n");
        FollowUpType type = decision.followUpType();
        String typeDesc =
                switch (type) {
                    case CLARIFY -> "CLARIFY（澄清：要求候选人对模糊部分给出具体例子或数据）";
                    case DEEPEN -> "DEEPEN（深挖：追问技术原理、实现细节或权衡取舍）";
                    case REDIRECT -> "REDIRECT（引导：礼貌地引导候选人回到问题本身）";
                    default -> type.name();
                };
        sb.append(typeDesc).append("\n\n");

        if (!context.followUpHints().isEmpty()) {
            sb.append("## 预设追问方向\n");
            for (int i = 0; i < context.followUpHints().size(); i++) {
                sb.append(i + 1).append(". ").append(context.followUpHints().get(i)).append('\n');
            }
            sb.append('\n');
        }

        if (!context.recentQuestions().isEmpty()) {
            sb.append("## 已问过的问题（避免重复）\n");
            for (String q : context.recentQuestions()) {
                sb.append("- ").append(q).append('\n');
            }
            sb.append('\n');
        }

        sb.append("请基于候选人的回答，生成一个追问问题。要求：\n");
        sb.append("- 只问一个问题，不要一次问多个\n");
        sb.append("- 语气自然，像真实面试官的追问\n");
        sb.append("- 不要输出标题、分析过程或 JSON，只输出追问问题本身");
        return sb.toString();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未提供" : value;
    }
}
