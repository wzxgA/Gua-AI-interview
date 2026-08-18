package com.aims.agent;

import java.util.List;

/**
 * 简历交叉验证工具结果：候选人回答与简历经历的事实比对证据。
 *
 * <p>作为 {@link ResumeCrossCheckTool} 的返回值，由 Spring AI 序列化为 JSON 回填给模型，仅作证据参考， 最终裁决由 LLM 完成。
 *
 * <p>F3 升级：新增 {@code conflictDetails}（实体级比对矛盾点明细）；无矛盾时为空列表。字段对齐 B §6 已实现的可解释性 （matchedSnippet /
 * matchedTerms / matchedFields / recallSource）。
 *
 * @param candidateName 候选人姓名
 * @param score 混合最终得分（vectorScore * 0.7 + keywordScore * 0.3；实体比对分支按一致/矛盾给参考值）
 * @param keywordScore 关键词匹配得分
 * @param matchedSnippet 命中高亮片段（命中词 ±30 字符）
 * @param matchedTerms 命中的查询关键词
 * @param matchedFields 命中字段（work / project / raw_text / skills / name）
 * @param recallSource 召回来源：VECTOR / HYBRID / ENTITY
 * @param conflictDetails F3 实体级矛盾点明细（company/project/period/skill）
 */
public record ResumeCrossCheckResult(
        String candidateName,
        double score,
        double keywordScore,
        String matchedSnippet,
        List<String> matchedTerms,
        List<String> matchedFields,
        String recallSource,
        List<ConflictDetail> conflictDetails) {

    /** 兼容 F2 的 7 参构造：无实体矛盾点明细。 */
    public ResumeCrossCheckResult(
            String candidateName,
            double score,
            double keywordScore,
            String matchedSnippet,
            List<String> matchedTerms,
            List<String> matchedFields,
            String recallSource) {
        this(
                candidateName,
                score,
                keywordScore,
                matchedSnippet,
                matchedTerms,
                matchedFields,
                recallSource,
                List.of());
    }

    /** 与简历一致（混合分数达标，且无实体矛盾点）。 */
    public boolean likelyConsistent() {
        return conflictDetails().isEmpty() && score >= 0.7;
    }

    /** 疑似简历矛盾（混合分数低或有实体矛盾点）。 */
    public boolean likelyConflict() {
        return !conflictDetails().isEmpty() || score < 0.5;
    }
}
