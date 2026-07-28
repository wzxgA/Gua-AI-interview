package com.aims.gateway.controller.position;

import com.aims.infra.persistence.entity.PositionEntity;
import java.time.Instant;

/**
 * 岗位响应。
 *
 * @param id 岗位 ID
 * @param title 岗位名称
 * @param department 所属部门
 * @param jdText JD 原文
 * @param requirementsJson 任职要求结构化 JSON
 * @param status 岗位状态
 * @param hasEmbedding 是否已有向量
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record PositionResponse(
        Long id,
        String title,
        String department,
        String jdText,
        String requirementsJson,
        String status,
        boolean hasEmbedding,
        Instant createdAt,
        Instant updatedAt) {

    /** 从持久化实体构建响应。 */
    public static PositionResponse from(PositionEntity entity) {
        return new PositionResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getDepartment(),
                entity.getJdText(),
                entity.getRequirementsJson(),
                entity.getStatus(),
                Boolean.TRUE.equals(entity.getHasEmbedding()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
