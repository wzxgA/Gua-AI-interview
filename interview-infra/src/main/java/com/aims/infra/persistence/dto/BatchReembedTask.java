package com.aims.infra.persistence.dto;

import java.time.Instant;

/**
 * 批量重新向量化任务状态。
 *
 * @param taskId 任务 ID
 * @param status 任务状态：PENDING / RUNNING / COMPLETED / FAILED
 * @param total 待处理总数
 * @param success 成功数量
 * @param failed 失败数量
 * @param startedAt 开始时间
 * @param finishedAt 完成时间
 * @param error 失败原因（整体失败时）
 */
public record BatchReembedTask(
        String taskId,
        String status,
        int total,
        int success,
        int failed,
        Instant startedAt,
        Instant finishedAt,
        String error) {}
