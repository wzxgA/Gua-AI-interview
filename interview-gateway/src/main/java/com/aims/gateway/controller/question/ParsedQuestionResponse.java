package com.aims.gateway.controller.question;

import java.util.List;

/**
 * 面经解析结果响应（供前端预览编辑，未落库）。
 *
 * @param category 分类
 * @param topic 主题
 * @param difficulty 难度
 * @param content 题干
 * @param standardAnswer 参考答案
 * @param tags 标签列表
 * @param matchedExistingId 与题库已有题目精确重复时的已有题目 ID（null 表示无重复）
 */
public record ParsedQuestionResponse(
        String category,
        String topic,
        String difficulty,
        String content,
        String standardAnswer,
        List<String> tags,
        Long matchedExistingId) {}
