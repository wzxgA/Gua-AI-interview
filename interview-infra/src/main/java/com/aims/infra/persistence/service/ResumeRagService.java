package com.aims.infra.persistence.service;

import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.ResumeSearchResult;

/** 简历 RAG 检索服务：基于 pgvector 语义相似度检索相关简历。 */
public interface ResumeRagService {

    /** 检索与查询最相关的简历（用于面试官交叉验证候选人陈述）。 */
    RagSearchResponse<ResumeSearchResult> search(String query, int topK, Double minScore);

    /** 检索指定候选人的简历匹配度。 */
    RagSearchResponse<ResumeSearchResult> search(
            String query, Long resumeId, int topK, Double minScore);
}
