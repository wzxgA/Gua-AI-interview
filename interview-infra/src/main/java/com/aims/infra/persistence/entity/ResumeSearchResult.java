package com.aims.infra.persistence.entity;

import java.util.List;

/**
 * 简历 RAG 检索结果。
 *
 * @param id 简历 ID
 * @param candidateName 候选人姓名
 * @param phone 手机号
 * @param email 邮箱
 * @param currentTitle 当前职位
 * @param yearsOfExperience 工作年限
 * @param skills 技能列表
 * @param score 混合最终得分（vectorScore * 0.7 + keywordScore * 0.3）
 * @param vectorScore 向量相似度得分（1 - 余弦距离，越大越相关）
 * @param keywordScore 关键词匹配得分（0-1，命中 raw_text=1.0、skills=0.9、candidate_name=0.8）
 * @param matchedSnippet 命中高亮片段（命中词 ±30 字符；关键词未命中时回退为职位/技能摘要）
 * @param embeddingModel 向量化使用的模型
 * @param matchedTerms 命中的查询关键词（关键词检索启用时按查询分词判定）
 * @param matchedFields 命中字段（raw_text / skills / name）
 * @param recallSource 召回来源：VECTOR（纯向量） / HYBRID（混合检索）
 */
public record ResumeSearchResult(
        Long id,
        String candidateName,
        String phone,
        String email,
        String currentTitle,
        Integer yearsOfExperience,
        List<String> skills,
        double score,
        double vectorScore,
        double keywordScore,
        String matchedSnippet,
        String embeddingModel,
        List<String> matchedTerms,
        List<String> matchedFields,
        String recallSource) {}
