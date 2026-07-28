package com.aims.infra.persistence.entity;

/**
 * 简历 RAG 检索结果。
 *
 * @param id 简历 ID
 * @param candidateName 候选人姓名
 * @param phone 手机号
 * @param email 邮箱
 * @param score 相似度得分（1 - 余弦距离，越大越相关）
 */
public record ResumeSearchResult(
        Long id, String candidateName, String phone, String email, double score) {}
