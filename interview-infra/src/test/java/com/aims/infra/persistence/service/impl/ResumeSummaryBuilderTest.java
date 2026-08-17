package com.aims.infra.persistence.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aims.infra.persistence.entity.ProjectExperienceEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.entity.WorkExperienceEntity;
import com.aims.infra.persistence.service.ResumeExperienceService;
import com.aims.infra.persistence.service.ResumeSummaryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link ResumeSummaryBuilder} 单元测试：经历表优先、parsed_json 回退、rawText 兜底、null 兜底。 */
class ResumeSummaryBuilderTest {

    private ResumeExperienceService experienceService;
    private ResumeSummaryBuilder builder;

    @BeforeEach
    void setUp() {
        experienceService = mock(ResumeExperienceService.class);
        when(experienceService.listWork(anyLong())).thenReturn(List.of());
        when(experienceService.listProject(anyLong())).thenReturn(List.of());
        builder = new ResumeSummaryBuilder(new ObjectMapper(), experienceService);
    }

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
    void build_experienceRowsPresent_prefersExperienceTables() {
        ResumeEntity e = new ResumeEntity();
        e.setId(4L);
        e.setParsedJson(
                "{\"candidateName\":\"李四\",\"workExperiences\":[{\"company\":\"parsed_json"
                        + "旧公司\",\"period\":\"2018.01 - 2019.01\"}]}");
        WorkExperienceEntity work = new WorkExperienceEntity();
        work.setResumeId(4L);
        work.setCompany("字节");
        work.setPosition("后端");
        work.setStartDate("2020.06");
        work.setEndDate("2022.05");
        work.setDescription("交易中台");
        ProjectExperienceEntity project = new ProjectExperienceEntity();
        project.setResumeId(4L);
        project.setName("风控引擎");
        project.setRole("核心开发");
        when(experienceService.listWork(4L)).thenReturn(List.of(work));
        when(experienceService.listProject(4L)).thenReturn(List.of(project));

        String summary = builder.build(e);

        assertTrue(summary.contains("姓名：李四"));
        assertTrue(summary.contains("工作经历"));
        assertTrue(summary.contains("字节"));
        assertTrue(summary.contains("2020.06 - 2022.05"));
        // 经历表优先：parsed_json 中的旧数据不应出现
        assertTrue(!summary.contains("旧公司"));
        assertTrue(summary.contains("项目经历"));
        assertTrue(summary.contains("风控引擎"));
    }

    @Test
    void build_experienceRowsOnly_noParsedJson_stillStructured() {
        ResumeEntity e = new ResumeEntity();
        e.setId(5L);
        e.setParsedJson(null);
        WorkExperienceEntity work = new WorkExperienceEntity();
        work.setCompany("腾讯");
        work.setPosition("前端");
        when(experienceService.listWork(5L)).thenReturn(List.of(work));

        String summary = builder.build(e);

        assertTrue(summary.contains("工作经历"));
        assertTrue(summary.contains("腾讯"));
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
