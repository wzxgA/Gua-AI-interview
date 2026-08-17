package com.aims.infra.persistence.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.service.ResumeSummaryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** {@link ResumeSummaryBuilder} 单元测试：结构化摘要、parsed_json 缺失回退、null 兜底。 */
class ResumeSummaryBuilderTest {

    private final ResumeSummaryBuilder builder = new ResumeSummaryBuilder(new ObjectMapper());

    @Test
    void build_nullResume_returnsNotProvided() {
        assertEquals("未提供", builder.build(null));
    }

    @Test
    void build_withParsedJson_buildsStructuredSummary() {
        ResumeEntity e = new ResumeEntity();
        e.setId(1L);
        e.setParsedJson(
                "{\"candidateName\":\"张三\",\"currentTitle\":\"Java工程师\",\"yearsOfExperience\":5,"
                    + "\"skills\":[\"Java\",\"Spring\"],"
                    + "\"workExperiences\":[{\"type\":\"工作\",\"company\":\"阿里\",\"title\":\"后端\",\"period\":\"2020.06"
                    + " - 2022.05\",\"description\":\"订单系统重构\"}],\"projectExperiences\":[]}");

        String summary = builder.build(e);

        assertTrue(summary.contains("姓名：张三"));
        assertTrue(summary.contains("当前职位：Java工程师"));
        assertTrue(summary.contains("工作年限：5 年"));
        assertTrue(summary.contains("技能：Java、Spring"));
        assertTrue(summary.contains("工作经历"));
        assertTrue(summary.contains("阿里"));
        assertTrue(summary.contains("2020.06 - 2022.05"));
        // 结构化摘要不应透传完整 parsed_json
        assertTrue(!summary.contains("projectExperiences"));
    }

    @Test
    void build_blankParsed_fallsBackToRawText() {
        ResumeEntity e = new ResumeEntity();
        e.setId(2L);
        e.setParsedJson(null);
        e.setRawText("候选人简历原始文本内容");

        assertEquals("候选人简历原始文本内容", builder.build(e));
    }

    @Test
    void build_blankEverything_returnsNotProvided() {
        ResumeEntity e = new ResumeEntity();
        e.setId(3L);
        e.setParsedJson(null);
        e.setRawText(null);

        assertEquals("未提供", builder.build(e));
    }
}
