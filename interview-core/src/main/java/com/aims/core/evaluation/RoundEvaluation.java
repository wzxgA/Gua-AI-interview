package com.aims.core.evaluation;

import java.io.Serializable;

/**
 * 单维度评分结果（AI 结构化输出的一部分）。
 *
 * @param dimension 评估维度
 * @param score 分值（1-5）
 * @param comment 评语
 * @param evidenceQuote 佐证原话（候选人回答引用）
 */
public record RoundEvaluation(
        EvaluationDimension dimension, int score, String comment, String evidenceQuote)
        implements Serializable {}
