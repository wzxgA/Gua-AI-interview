package com.aims.core.question;

import java.util.List;

/**
 * 面经解析出的规范化题目（AI 结构化输出中间模型，未落库）。
 *
 * @param category 分类（对应 {@link QuestionCategory} 名称）
 * @param topic 主题
 * @param difficulty 难度（对应 {@link Difficulty} 名称）
 * @param content 题干
 * @param standardAnswer 参考答案（面经中若包含回答则提炼，否则为空字符串）
 * @param tags 标签列表
 */
public record ParsedQuestion(
        String category,
        String topic,
        String difficulty,
        String content,
        String standardAnswer,
        List<String> tags) {}
