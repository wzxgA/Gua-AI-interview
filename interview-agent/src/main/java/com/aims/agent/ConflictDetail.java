package com.aims.agent;

/**
 * 简历交叉验证的矛盾点明细（F3 实体级比对）：精确指认公司/项目/时间/技能哪个字段对不上。
 *
 * @param conflictField 矛盾字段：company / project / period / skill
 * @param expected 简历（经历表）中的值；简历未提及时为 null
 * @param actual 候选人回答声称的值
 * @param evidenceSnippet 证据片段（命中经历描述或回答原文，便于模型/报告引用）
 */
public record ConflictDetail(
        String conflictField, String expected, String actual, String evidenceSnippet) {}
