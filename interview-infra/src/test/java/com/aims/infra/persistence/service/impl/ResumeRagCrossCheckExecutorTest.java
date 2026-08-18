package com.aims.infra.persistence.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aims.agent.ResumeCrossCheckResult;
import com.aims.core.interview.ConflictDetail;
import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.ProjectExperienceEntity;
import com.aims.infra.persistence.entity.ResumeSearchResult;
import com.aims.infra.persistence.entity.WorkExperienceEntity;
import com.aims.infra.persistence.mapper.ProjectExperienceMapper;
import com.aims.infra.persistence.mapper.WorkExperienceMapper;
import com.aims.infra.persistence.service.ResumeRagService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link ResumeRagCrossCheckExecutor} F3 实体级比对测试：一致 / 简历未提及 / period 冲突 / 无实体回退全文。 */
class ResumeRagCrossCheckExecutorTest {

    private ResumeRagService resumeRagService;
    private WorkExperienceMapper workMapper;
    private ProjectExperienceMapper projectMapper;
    private ResumeRagCrossCheckExecutor executor;

    @BeforeEach
    void setUp() {
        resumeRagService = mock(ResumeRagService.class);
        workMapper = mock(WorkExperienceMapper.class);
        projectMapper = mock(ProjectExperienceMapper.class);
        executor = new ResumeRagCrossCheckExecutor(resumeRagService, workMapper, projectMapper);
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectList(any())).thenReturn(List.of());
    }

    private WorkExperienceEntity work(String company, String start, String end) {
        WorkExperienceEntity w = new WorkExperienceEntity();
        w.setResumeId(1L);
        w.setCompany(company);
        w.setStartDate(start);
        w.setEndDate(end);
        w.setDescription("订单系统重构");
        return w;
    }

    @Test
    void crossCheck_companyMatched_consistent() {
        when(workMapper.selectList(any())).thenReturn(List.of(work("字节", "2020.06", "2022.05")));

        ResumeCrossCheckResult r = executor.crossCheck(1L, "我在字节负责订单系统，2020年到2022年", null);

        assertNotNull(r);
        assertEquals("ENTITY", r.recallSource());
        assertTrue(r.conflictDetails().isEmpty());
        assertTrue(r.likelyConsistent());
        assertEquals(0.9, r.score(), 0.001);
        assertEquals(List.of("work"), r.matchedFields());
    }

    @Test
    void crossCheck_companyHint_notInResume_conflict() {
        when(workMapper.selectList(any())).thenReturn(List.of(work("字节", "2020.06", "2022.05")));

        ResumeCrossCheckResult r = executor.crossCheck(1L, "我在阿里巴巴负责电商中台", "阿里巴巴");

        assertNotNull(r);
        assertEquals("ENTITY", r.recallSource());
        assertTrue(r.likelyConflict());
        assertEquals(1, r.conflictDetails().size());
        ConflictDetail d = r.conflictDetails().get(0);
        assertEquals("company", d.conflictField());
        assertNull(d.expected());
        assertEquals("阿里巴巴", d.actual());
    }

    @Test
    void crossCheck_periodConflict_detected() {
        when(workMapper.selectList(any())).thenReturn(List.of(work("字节", "2020.06", "2022.05")));

        ResumeCrossCheckResult r = executor.crossCheck(1L, "我在字节工作，2016年到2018年", null);

        assertNotNull(r);
        assertTrue(r.likelyConflict());
        assertEquals(1, r.conflictDetails().size());
        ConflictDetail d = r.conflictDetails().get(0);
        assertEquals("period", d.conflictField());
        assertEquals("2020 - 2022", d.expected());
        assertEquals("2016 - 2018", d.actual());
    }

    @Test
    void crossCheck_periodWithinRange_noConflict() {
        when(workMapper.selectList(any())).thenReturn(List.of(work("字节", "2020.06", "2022.05")));

        ResumeCrossCheckResult r = executor.crossCheck(1L, "2021年我在字节负责订单系统", null);

        assertNotNull(r);
        assertTrue(r.conflictDetails().isEmpty());
        assertTrue(r.likelyConsistent());
    }

    @Test
    void crossCheck_synonymCompany_containedMatch_noConflict() {
        // F5 包含匹配：回答"字节跳动"可命中表内"字节"，不误判"未提及"
        when(workMapper.selectList(any())).thenReturn(List.of(work("字节", "2020.06", "2022.05")));

        ResumeCrossCheckResult r = executor.crossCheck(1L, "2021年我在字节跳动负责订单系统", "字节跳动");

        assertNotNull(r);
        assertTrue(r.conflictDetails().isEmpty());
        assertTrue(r.likelyConsistent());
        assertEquals(List.of("work"), r.matchedFields());
    }

    @Test
    void crossCheck_noEntity_fallsBackToRag() {
        when(workMapper.selectList(any())).thenReturn(List.of(work("字节", "2020.06", "2022.05")));
        ResumeSearchResult searchResult =
                new ResumeSearchResult(
                        1L,
                        "张三",
                        "13800000000",
                        "a@b.com",
                        "Java 工程师",
                        5,
                        List.of("Java"),
                        0.62,
                        0.8,
                        0.2,
                        "技能：Java、Redis",
                        "text-embedding-v4",
                        List.of("Java"),
                        List.of("skills"),
                        "HYBRID");
        when(resumeRagService.search(anyString(), any(), anyInt(), any()))
                .thenReturn(
                        new RagSearchResponse<>(
                                List.of(searchResult),
                                new RagSearchResponse.SearchMetrics(1, 1, 2, 1)));

        // 回答未提及任何经历表实体、且无 companyHint -> 回退全文检索
        ResumeCrossCheckResult r = executor.crossCheck(1L, "我对微服务架构很有心得", null);

        assertNotNull(r);
        assertEquals("HYBRID", r.recallSource());
        assertEquals("张三", r.candidateName());
        assertTrue(r.conflictDetails().isEmpty());
    }

    @Test
    void crossCheck_projectMatched_consistent() {
        ProjectExperienceEntity p = new ProjectExperienceEntity();
        p.setResumeId(1L);
        p.setName("风控引擎");
        p.setStartDate("2021.01");
        p.setEndDate("2021.12");
        when(projectMapper.selectList(any())).thenReturn(List.of(p));

        ResumeCrossCheckResult r = executor.crossCheck(1L, "风控引擎是我主导的，2021年上线", null);

        assertNotNull(r);
        assertEquals("ENTITY", r.recallSource());
        assertTrue(r.conflictDetails().isEmpty());
        assertEquals(List.of("project"), r.matchedFields());
    }

    @Test
    void crossCheck_invalidArgs_returnsNull() {
        assertNull(executor.crossCheck(null, "answer", null));
        assertNull(executor.crossCheck(1L, " ", null));
    }
}
