package com.aims.gateway.controller.question;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 创建题目请求。
 *
 * @param category 分类（TECHNICAL / BEHAVIORAL / PROJECT）
 * @param topic 主题
 * @param difficulty 难度（EASY / MEDIUM / HARD）
 * @param content 题干
 * @param standardAnswer 标准答案
 * @param tags 标签列表
 */
public record CreateQuestionRequest(
        @NotBlank(message = "category 不能为空") String category,
        @NotBlank(message = "topic 不能为空") String topic,
        @NotBlank(message = "difficulty 不能为空") String difficulty,
        @NotBlank(message = "content 不能为空") String content,
        String standardAnswer,
        List<String> tags) {}
