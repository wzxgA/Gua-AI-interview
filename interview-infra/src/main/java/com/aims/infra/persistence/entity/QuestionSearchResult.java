package com.aims.infra.persistence.entity;

/**
 * 向量检索结果（含相似度分数）。
 *
 * @param id 题目 ID
 * @param category 分类
 * @param topic 主题
 * @param difficulty 难度
 * @param content 题干
 * @param standardAnswer 标准答案
 * @param score 相似度分数（1 - 余弦距离，越高越相似）
 */
public record QuestionSearchResult(
        Long id,
        String category,
        String topic,
        String difficulty,
        String content,
        String standardAnswer,
        double score) {}
