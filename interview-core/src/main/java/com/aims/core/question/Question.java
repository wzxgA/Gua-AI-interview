package com.aims.core.question;

import java.time.Instant;
import java.util.List;

/**
 * 题库领域模型。
 *
 * @param id 题目 ID
 * @param category 题目分类（对应 {@link QuestionCategory} 名称）
 * @param topic 主题
 * @param difficulty 难度（对应 {@link Difficulty} 名称）
 * @param content 题干
 * @param standardAnswer 标准答案
 * @param tags 标签
 * @param embedding 题目向量
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record Question(
        Long id,
        String category,
        String topic,
        String difficulty,
        String content,
        String standardAnswer,
        List<String> tags,
        String embedding,
        Instant createdAt,
        Instant updatedAt) {}
