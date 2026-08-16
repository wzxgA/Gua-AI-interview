package com.aims.infra.persistence.dto;

import java.time.Instant;
import java.util.List;

/**
 * 面经解析异步任务状态。
 *
 * @param taskId 任务 ID
 * @param status 任务状态：RUNNING / SUCCESS / FAILED
 * @param results 解析结果（SUCCESS 时非空，未落库供预览）
 * @param message 失败原因（FAILED 时）
 * @param startedAt 开始时间
 * @param finishedAt 完成时间
 */
public record InterviewNoteParseTask(
        String taskId,
        String status,
        List<QuestionParseResult> results,
        String message,
        Instant startedAt,
        Instant finishedAt) {}
