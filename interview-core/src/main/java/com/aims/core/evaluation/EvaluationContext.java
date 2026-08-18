package com.aims.core.evaluation;

import com.aims.core.interview.ConflictDetail;
import java.util.List;

/**
 * 评估上下文，传递给 {@code EvaluatorAgent}。
 *
 * @param sessionId 会话 ID
 * @param roundId 轮次 ID
 * @param seq 轮次序号（追问为 null）
 * @param parentSeq 追问所属主问题 seq（主问题为 null）
 * @param followUpIndex 同一主问题下第几次追问（主问题为 null）
 * @param question 面试问题
 * @param answer 候选人回答
 * @param positionTitle 岗位名称
 * @param jdText 岗位 JD
 * @param resumeSummary 简历摘要
 * @param conflictDetails 该轮简历交叉验证矛盾点（无矛盾为空列表，供评估引用）
 */
public record EvaluationContext(
        Long sessionId,
        Long roundId,
        Integer seq,
        Integer parentSeq,
        Integer followUpIndex,
        String question,
        String answer,
        String positionTitle,
        String jdText,
        String resumeSummary,
        List<ConflictDetail> conflictDetails) {

    public EvaluationContext {
        conflictDetails = conflictDetails == null ? List.of() : List.copyOf(conflictDetails);
    }
}
