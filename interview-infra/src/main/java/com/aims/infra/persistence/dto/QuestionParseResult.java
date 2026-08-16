package com.aims.infra.persistence.dto;

import com.aims.core.question.ParsedQuestion;

/**
 * 面经解析结果（供预览，未落库）。
 *
 * @param parsed 规范化题目
 * @param matchedExistingId 与题库已有题目按题干精确重复时的已有题目 ID（null 表示无重复）
 */
public record QuestionParseResult(ParsedQuestion parsed, Long matchedExistingId) {}
