package com.aims.gateway.controller.resume;

import com.aims.core.resume.ParsedResume;
import java.time.Instant;

/**
 * 简历响应 DTO。
 *
 * @param id 简历 ID
 * @param candidateName 候选人姓名
 * @param phone 联系电话
 * @param email 邮箱
 * @param rawText 简历原文摘要（截断展示）
 * @param parseStatus 解析状态
 * @param parseError 解析失败原因
 * @param parsedAt 解析完成时间
 * @param embeddingStatus 向量化状态
 * @param embeddingError 向量化失败原因
 * @param embeddedAt 向量化完成时间
 * @param parsedResume 解析后的结构化简历（未解析时为 null）
 * @param fileUrl 简历文件 URL
 * @param hasEmbedding 是否已生成向量
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ResumeResponse(
        Long id,
        String candidateName,
        String phone,
        String email,
        String rawText,
        String parseStatus,
        String parseError,
        Instant parsedAt,
        String embeddingStatus,
        String embeddingError,
        Instant embeddedAt,
        ParsedResume parsedResume,
        String fileUrl,
        boolean hasEmbedding,
        Instant createdAt,
        Instant updatedAt) {}
