package com.aims.infra.persistence.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.aims.core.resume.ParsedResume;
import com.aims.core.resume.ProjectExperience;
import com.aims.core.resume.WorkExperience;
import com.aims.infra.persistence.entity.ProjectExperienceEntity;
import com.aims.infra.persistence.entity.ProjectHighlightEntity;
import com.aims.infra.persistence.entity.WorkExperienceEntity;
import com.aims.infra.persistence.mapper.ProjectExperienceMapper;
import com.aims.infra.persistence.mapper.ProjectHighlightMapper;
import com.aims.infra.persistence.mapper.WorkExperienceMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link ResumeExperienceServiceImpl} 单元测试：period 拆分、项目亮点拆行、幂等重建。 */
class ResumeExperienceServiceImplTest {

    private WorkExperienceMapper workMapper;
    private ProjectExperienceMapper projectMapper;
    private ProjectHighlightMapper highlightMapper;
    private ResumeExperienceServiceImpl service;

    @BeforeEach
    void setUp() {
        workMapper = mock(WorkExperienceMapper.class);
        projectMapper = mock(ProjectExperienceMapper.class);
        highlightMapper = mock(ProjectHighlightMapper.class);
        service = new ResumeExperienceServiceImpl(workMapper, projectMapper, highlightMapper);
    }

    @Test
    void syncFromParsed_workPeriodSplitAndInsert() {
        ParsedResume parsed =
                new ParsedResume(
                        "张三",
                        null,
                        null,
                        5,
                        null,
                        "工程师",
                        List.of("Java"),
                        List.of(new WorkExperience("工作", "阿里", "后端", "2020.06 - 2022.05", "订单系统")),
                        List.of(),
                        List.of());

        service.syncFromParsed(1L, parsed);

        verify(workMapper)
                .insert(
                        argThat(
                                (WorkExperienceEntity e) ->
                                        e.getResumeId() == 1L
                                                && "阿里".equals(e.getCompany())
                                                && "2020.06".equals(e.getStartDate())
                                                && "2022.05".equals(e.getEndDate())));
        verify(projectMapper).delete(any());
    }

    @Test
    void syncFromParsed_projectHighlightsSplitInOrder() {
        ParsedResume parsed =
                new ParsedResume(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(
                                new ProjectExperience(
                                        "订单系统",
                                        "开发",
                                        "2021 - 2022",
                                        "desc",
                                        List.of("亮点A", "亮点B"))),
                        List.of());

        service.syncFromParsed(1L, parsed);

        verify(projectMapper).insert(any(ProjectExperienceEntity.class));
        verify(highlightMapper)
                .insert(
                        argThat(
                                (ProjectHighlightEntity h) ->
                                        "亮点A".equals(h.getContent()) && h.getSortOrder() == 0));
        verify(highlightMapper)
                .insert(
                        argThat(
                                (ProjectHighlightEntity h) ->
                                        "亮点B".equals(h.getContent()) && h.getSortOrder() == 1));
    }

    @Test
    void syncFromParsed_nullParsed_clearsOnlyNoInsert() {
        service.syncFromParsed(1L, null);

        verify(workMapper).delete(any());
        verify(projectMapper).delete(any());
        verify(highlightMapper, never()).insert(any(ProjectHighlightEntity.class));
    }

    @Test
    void splitPeriod_noDash_keepsStartOnly() {
        ParsedResume parsed =
                new ParsedResume(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(new WorkExperience("工作", "字节", "后端", "2020.06", null)),
                        List.of(),
                        List.of());

        service.syncFromParsed(1L, parsed);

        verify(workMapper)
                .insert(
                        argThat(
                                (WorkExperienceEntity e) ->
                                        "2020.06".equals(e.getStartDate())
                                                && e.getEndDate() == null));
        assertEquals(1, parsed.workExperiences().size());
    }
}
