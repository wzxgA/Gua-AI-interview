package com.aims.agent;

import com.aims.core.interview.SummaryContext;

/**
 * 滚动摘要 Agent 接口。
 *
 * <p>负责将早期面试 Q&A 压缩为一段累计摘要，替代原始长文本注入 LLM 上下文。
 */
public interface SummaryAgent {

    /**
     * 生成滚动摘要。
     *
     * @param context 摘要上下文（包含 previousSummary 和本次需压缩的 Q&A）
     * @return 新的累计摘要文本；生成失败时降级返回 previousSummary
     */
    String summarize(SummaryContext context);
}
