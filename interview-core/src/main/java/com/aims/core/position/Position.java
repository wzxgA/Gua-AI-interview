package com.aims.core.position;

import java.time.Instant;

/**
 * 岗位领域模型。
 *
 * @param id 岗位 ID
 * @param title 岗位名称
 * @param department 所属部门
 * @param jdText JD 原文
 * @param requirementsJson 任职要求结构化 JSON
 * @param status 岗位状态
 * @param embedding 岗位向量（用于检索匹配）
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record Position(
        Long id,
        String title,
        String department,
        String jdText,
        String requirementsJson,
        PositionStatus status,
        String embedding,
        Instant createdAt,
        Instant updatedAt) {}
