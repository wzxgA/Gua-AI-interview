package com.aims.core.resume;

import java.time.Instant;

/**
 * 简历领域模型。
 *
 * @param id 简历 ID
 * @param candidateName 候选人姓名
 * @param phone 联系电话
 * @param email 邮箱
 * @param rawText 简历原文
 * @param parsedResume 解析后的结构
 * @param fileUrl 简历文件 URL
 * @param parseStatus 解析状态
 * @param embedding 简历向量
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record Resume(
        Long id,
        String candidateName,
        String phone,
        String email,
        String rawText,
        ParsedResume parsedResume,
        String fileUrl,
        ResumeStatus parseStatus,
        String embedding,
        Instant createdAt,
        Instant updatedAt) {}
