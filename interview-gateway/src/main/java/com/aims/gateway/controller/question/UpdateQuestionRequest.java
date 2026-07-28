package com.aims.gateway.controller.question;

import java.util.List;

/**
 * 更新题目请求（所有字段可选，null 表示不更新）。
 *
 * @param category 分类
 * @param topic 主题
 * @param difficulty 难度
 * @param content 题干
 * @param standardAnswer 标准答案
 * @param tags 标签列表
 */
public record UpdateQuestionRequest(
        String category,
        String topic,
        String difficulty,
        String content,
        String standardAnswer,
        List<String> tags) {}
