package com.aims.infra.persistence.service;

import com.aims.infra.persistence.dto.QuestionFilter;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import java.util.List;

/** 题库 RAG 检索服务：基于 pgvector 语义相似度检索相关题目。 */
public interface QuestionRagService {

    /** 按查询文本检索 Top-K 相关题目。 */
    List<QuestionSearchResult> search(String query, int topK);

    /** 带过滤条件的检索（category/difficulty）。 */
    List<QuestionSearchResult> search(String query, QuestionFilter filter, int topK);
}
