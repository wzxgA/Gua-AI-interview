package com.aims.agent.orchestration.state;

/**
 * 轮次初始化数据 DTO：解耦 InterviewStateFactory 与持久化实体类。
 *
 * <p>由调用方（gateway/infra 层）从 {@code InterviewRoundEntity} 映射到此 record， 避免 interview-agent →
 * interview-infra 的循环依赖。
 *
 * @param id 轮次 ID
 * @param seq 轮次序号
 * @param question 面试问题
 * @param answer 候选人回答（null 表示未回答）
 * @param parentSeq 父轮次序号（主问题为 null）
 * @param followUpIndex 追问索引（非追问为 null）
 * @param followUpType 追问类型字符串（对应 {@link com.aims.core.interview.FollowUpType} 枚举名）
 * @since 1.1.0
 */
public record RoundInitData(
        Long id,
        Integer seq,
        String question,
        String answer,
        Integer parentSeq,
        Integer followUpIndex,
        String followUpType) {}
