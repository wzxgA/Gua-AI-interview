package com.aims.core.report;

import java.util.Map;

/**
 * 报告结构化输出（AI 返回）。
 *
 * @param summary 综合评述
 * @param dimensions 各维度聚合评分（dimension name -> {avgScore, count}）
 * @param recommendation 录用建议
 */
public record ReportResult(
        String summary, Map<String, DimensionScore> dimensions, Recommendation recommendation) {

    /** 单维度评分聚合。 */
    public record DimensionScore(double avgScore, int count) {}
}
