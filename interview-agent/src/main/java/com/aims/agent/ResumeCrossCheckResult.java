package com.aims.agent;

import java.util.List;

/**
 * 简历交叉验证工具结果：候选人回答与简历经历的事实比对证据。
 *
 * <p>作为 {@link ResumeCrossCheckTool} 的返回值，由 Spring AI 序列化为 JSON 回填给模型，仅作证据参考， 最终裁决由 LLM 完成。字段对齐 B
 * §6 已实现的可解释性（matchedSnippet / matchedTerms / matchedFields / recallSource）。
 *
 * @param candidateName 候选人姓名
 * @param score 混合最终得分（vectorScore * 0.7 + keywordScore * 0.3）
 * @param keywordScore 关键词匹配得分
 * @param matchedSnippet 命中高亮片段（命中词 ±30 字符）
 * @param matchedTerms 命中的查询关键词
 * @param matchedFields 命中字段（raw_text / skills / name）
 * @param recallSource 召回来源：VECTOR / HYBRID
 */
public record ResumeCrossCheckResult(
        String candidateName,
        double score,
        double keywordScore,
        String matchedSnippet,
        List<String> matchedTerms,
        List<String> matchedFields,
        String recallSource) {

    /** 与简历一致（混合分数达标）。 */
    public boolean likelyConsistent() {
        return score >= 0.7;
    }

    /** 疑似简历矛盾（混合分数低）。 */
    public boolean likelyConflict() {
        return score < 0.5;
    }
}
