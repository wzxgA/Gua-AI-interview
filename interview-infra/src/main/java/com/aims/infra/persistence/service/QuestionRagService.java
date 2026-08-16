package com.aims.infra.persistence.service;

import com.aims.infra.persistence.dto.QuestionFilter;
import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.QuestionSearchResult;

/** 题库 RAG 检索服务：向量 + 关键词混合检索，返回结果与过程指标。 */
public interface QuestionRagService {

    /** 按查询文本检索 Top-K 相关题目。 */
    RagSearchResponse<QuestionSearchResult> search(String query, int topK);

    /** 带过滤条件的检索（category/difficulty）。 */
    RagSearchResponse<QuestionSearchResult> search(String query, QuestionFilter filter, int topK);
}
