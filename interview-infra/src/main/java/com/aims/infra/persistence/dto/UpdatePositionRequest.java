package com.aims.infra.persistence.dto;

/**
 * 更新岗位请求。所有字段可选，仅更新非 null 字段。
 *
 * @param title 岗位名称
 * @param department 所属部门
 * @param jdText JD 原文
 * @param requirementsJson 任职要求结构化 JSON
 * @param status 岗位状态（ACTIVE / INACTIVE）
 */
public record UpdatePositionRequest(
        String title, String department, String jdText, String requirementsJson, String status) {}
