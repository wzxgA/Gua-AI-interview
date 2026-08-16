package com.aims.gateway.controller.question;

import java.util.List;

/**
 * 面经解析异步任务响应（提交/轮询共用）。
 *
 * @param taskId 任务 ID
 * @param status 任务状态：RUNNING / SUCCESS / FAILED / NOT_FOUND
 * @param message 失败原因（FAILED 时）
 * @param results 解析结果（SUCCESS 时非空，未落库供预览编辑）
 */
public record InterviewNoteParseTaskResponse(
        String taskId, String status, String message, List<ParsedQuestionResponse> results) {}
