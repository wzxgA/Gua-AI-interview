package com.aims.infra.persistence.dto;

/**
 * 题库 RAG 检索过滤条件。
 *
 * @param category 题目分类（可空，为空时不参与过滤）
 * @param difficulty 难度（可空，为空时不参与过滤）
 */
public record QuestionFilter(String category, String difficulty) {}
