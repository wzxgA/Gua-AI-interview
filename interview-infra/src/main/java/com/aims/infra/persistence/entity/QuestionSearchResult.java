package com.aims.infra.persistence.entity;

import java.util.List;

/**
 * 题库向量检索结果（含混合检索得分与可解释性字段）。
 *
 * @param id 题目 ID
 * @param category 分类
 * @param topic 主题
 * @param difficulty 难度
 * @param content 题干
 * @param standardAnswer 标准答案
 * @param score 混合最终得分（vectorScore * 0.7 + keywordScore * 0.3；纯向量降级时等于 vectorScore）
 * @param vectorScore 向量相似度得分（1 - 余弦距离，越大越相关）
 * @param keywordScore 关键词匹配得分（0-1，命中 content=1.0、topic=0.9、category=0.8；纯向量降级时为 0）
 * @param matchedTerms 命中的查询关键词（关键词检索启用时按查询分词判定）
 * @param matchedFields 命中字段（content / topic / category）
 * @param highlightSnippet 命中文本片段（用于解释"为什么命中"）
 * @param recallSource 召回来源：VECTOR（纯向量） / HYBRID（混合检索）
 */
public record QuestionSearchResult(
        Long id,
        String category,
        String topic,
        String difficulty,
        String content,
        String standardAnswer,
        double score,
        double vectorScore,
        double keywordScore,
        List<String> matchedTerms,
        List<String> matchedFields,
        String highlightSnippet,
        String recallSource) {}
