package com.aims.gateway.controller.question;

import java.time.Instant;
import java.util.List;

/**
 * 题目响应。
 *
 * @param id 题目 ID
 * @param category 分类
 * @param topic 主题
 * @param difficulty 难度
 * @param content 题干
 * @param standardAnswer 标准答案
 * @param tags 标签列表
 * @param hasEmbedding 是否已向量化
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record QuestionResponse(
        Long id,
        String category,
        String topic,
        String difficulty,
        String content,
        String standardAnswer,
        List<String> tags,
        boolean hasEmbedding,
        Instant createdAt,
        Instant updatedAt) {}
