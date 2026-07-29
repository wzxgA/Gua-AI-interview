package com.aims.infra.persistence.dto;

import java.util.List;

/**
 * RAG 检索响应，包含结果列表和检索过程指标。
 *
 * @param results 检索结果列表
 * @param metrics 检索过程指标
 * @param <T> 结果类型
 */
public record RagSearchResponse<T>(List<T> results, SearchMetrics metrics) {

    /**
     * 检索过程指标。
     *
     * @param embeddingMs Embedding 生成耗时（毫秒）
     * @param sqlMs SQL 检索耗时（毫秒）
     * @param totalMs 总耗时（毫秒）
     * @param resultCount 返回结果数量
     */
    public record SearchMetrics(long embeddingMs, long sqlMs, long totalMs, int resultCount) {}
}
