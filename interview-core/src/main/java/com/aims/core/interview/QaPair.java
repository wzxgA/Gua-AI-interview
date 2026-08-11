package com.aims.core.interview;

import java.io.Serializable;

/**
 * 单组问答对，用于摘要生成、评估与报告的输入。
 *
 * @param seq 主问题序号（从 1 开始）；追问 Q&A 沿用其主问题 seq
 * @param question 面试问题
 * @param answer 候选人回答
 * @param followUpIndex 追问索引（null=主问题，1..3=该主问题下第 N 次追问）
 * @param followUpType 追问类型（null=主问题）
 */
public record QaPair(
        int seq, String question, String answer, Integer followUpIndex, FollowUpType followUpType)
        implements Serializable {

    /** 兼容构造：主问题 Q&A（followUpIndex/followUpType 为 null）。 */
    public QaPair(int seq, String question, String answer) {
        this(seq, question, answer, null, null);
    }

    /** 是否为追问 Q&A。 */
    public boolean isFollowUp() {
        return followUpIndex != null;
    }
}
