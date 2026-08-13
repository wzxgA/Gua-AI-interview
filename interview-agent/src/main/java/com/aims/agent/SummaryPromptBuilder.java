package com.aims.agent;

import com.aims.core.interview.QaPair;
import com.aims.core.interview.SummaryContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 滚动摘要 Prompt 构建器。
 *
 * <p>System Prompt 指导 LLM 将早期 Q&A 压缩为累计摘要； User Prompt 包含岗位、已有摘要和本轮待压缩 Q&A。
 */
@Component
public class SummaryPromptBuilder {

    private static final String SYSTEM_PROMPT =
            """
            你是一名面试记录摘要助手。你的任务是将面试 Q&A 压缩为一段简洁的累计摘要。

            要求：
            1. 保留候选人的核心观点、技术亮点和不足之处。
            2. 按主题归类（技术深度、项目经验、沟通表达、问题解决等）。
            3. 每个主题用 1-3 句话概括，不要逐题复述。
            4. 如果已有此前摘要，在其基础上增量更新，保留已有信息并补充新内容。
            5. 总长度控制在 400 字以内。
            6. 使用中文，客观陈述，不带主观评价词。
            7. 直接输出摘要内容，不要添加"摘要："等前缀。
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserPrompt(SummaryContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("面试岗位：").append(context.positionTitle()).append("\n\n");

        String prev = context.previousSummary();
        if (prev != null && !prev.isBlank()) {
            sb.append("【此前摘要】\n").append(prev).append("\n\n");
        }

        sb.append("【本轮 Q&A（第 ")
                .append(context.lastSummarizedSeq() + 1)
                .append("-")
                .append(context.lastSummarizedSeq() + context.roundsToSummarize().size())
                .append(" 轮）】\n");

        List<QaPair> rounds = context.roundsToSummarize();
        for (QaPair qa : rounds) {
            sb.append("Q").append(qa.seq()).append("：").append(qa.question()).append("\n");
            sb.append("A")
                    .append(qa.seq())
                    .append("：")
                    .append(truncate(qa.answer(), 500))
                    .append("\n\n");
        }

        sb.append("请生成更新后的累计摘要。");
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
