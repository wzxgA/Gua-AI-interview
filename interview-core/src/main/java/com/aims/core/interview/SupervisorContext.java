package com.aims.core.interview;

import java.io.Serializable;

/**
 * 面试总指挥决策输入：实时会话状态快照。
 *
 * @param sessionId 会话 ID
 * @param currentSeq 当前题序
 * @param totalRounds 计划题数（主问题）
 * @param answeredMainCount 已完成主问题数（不含追问，进度判定的主口径）
 * @param answeredCount 已回答轮次（含追问，仅作参考，可能大于计划题数）
 * @param followUpCount 当前题追问次数
 * @param elapsedMs 已耗时（毫秒）
 * @param avgScore 回答质量均值（可空，评估未就绪时为 null）
 */
public record SupervisorContext(
        Long sessionId,
        int currentSeq,
        int totalRounds,
        int answeredMainCount,
        int answeredCount,
        int followUpCount,
        long elapsedMs,
        Double avgScore)
        implements Serializable {} // 与 interview-core 领域对象序列化惯例保持一致
