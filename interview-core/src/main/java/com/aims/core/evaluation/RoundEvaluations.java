package com.aims.core.evaluation;

import java.util.List;

/**
 * 单轮五维度评分结果（AI 结构化输出类型）。
 *
 * @param evaluations 五个维度的评分列表
 */
public record RoundEvaluations(List<RoundEvaluation> evaluations) {}
