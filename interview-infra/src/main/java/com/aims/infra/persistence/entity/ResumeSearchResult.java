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
 * @param score 相似度得分（1 - 余弦距离，越大越相关）
 * @param matchedSnippet 匹配片段（从结构化字段生成，用于解释匹配原因）
 * @param embeddingModel 向量化使用的模型
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
        String matchedSnippet,
        String embeddingModel) {}
