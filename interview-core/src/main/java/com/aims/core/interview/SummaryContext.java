package com.aims.core.interview;

import java.util.List;

/**
 * 摘要生成上下文，传递给 SummaryAgent。
 *
 * @param sessionId 面试会话 ID
 * @param positionTitle 岗位名称
 * @param previousSummary 此前累计摘要（首次为 null）
 * @param roundsToSummarize 本次需压缩的 Q&A 列表（通常为 5 组）
 * @param lastSummarizedSeq 上次已摘要到的轮次序号（首次为 0）
 */
public record SummaryContext(
        Long sessionId,
        String positionTitle,
        String previousSummary,
        List<QaPair> roundsToSummarize,
        int lastSummarizedSeq) {}
