package com.aims.core.question;

import java.util.List;

/**
 * 面经解析结果包装（对齐 {@link com.aims.core.interview.InterviewPlan} 结构化输出模式）。
 *
 * @param questions 解析出的题目列表
 */
public record ParsedQuestionList(List<ParsedQuestion> questions) {}
