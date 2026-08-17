package com.aims.infra.persistence.service.impl;

import com.aims.core.resume.ParsedResume;
import com.aims.core.resume.ProjectExperience;
import com.aims.core.resume.WorkExperience;
import com.aims.infra.persistence.entity.ProjectExperienceEntity;
import com.aims.infra.persistence.entity.ProjectHighlightEntity;
import com.aims.infra.persistence.entity.WorkExperienceEntity;
import com.aims.infra.persistence.mapper.ProjectExperienceMapper;
import com.aims.infra.persistence.mapper.ProjectHighlightMapper;
import com.aims.infra.persistence.mapper.WorkExperienceMapper;
import com.aims.infra.persistence.service.ResumeExperienceService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ResumeExperienceService} 实现：删旧插新全量重建经历行，保证与 parsed_json 双写一致。
 *
 * <p>period（如 "2020.06 - 2022.05"）拆分为 startDate/endDate；项目亮点按顺序拆行（sortOrder）。
 */
@Service
public class ResumeExperienceServiceImpl implements ResumeExperienceService {

    private static final Logger log = LoggerFactory.getLogger(ResumeExperienceServiceImpl.class);

    private final WorkExperienceMapper workMapper;
    private final ProjectExperienceMapper projectMapper;
    private final ProjectHighlightMapper highlightMapper;

    public ResumeExperienceServiceImpl(
            WorkExperienceMapper workMapper,
            ProjectExperienceMapper projectMapper,
            ProjectHighlightMapper highlightMapper) {
        this.workMapper = workMapper;
        this.projectMapper = projectMapper;
        this.highlightMapper = highlightMapper;
    }

    @Override
    @Transactional
    public void syncFromParsed(Long resumeId, ParsedResume parsed) {
        if (resumeId == null) {
            return;
        }
        deleteByResumeId(resumeId);
        if (parsed == null) {
            return;
        }

        if (parsed.workExperiences() != null) {
            for (WorkExperience we : parsed.workExperiences()) {
                if (we == null) {
                    continue;
                }
                WorkExperienceEntity e = new WorkExperienceEntity();
                e.setResumeId(resumeId);
                e.setExpType(we.type());
                e.setCompany(we.company());
                e.setPosition(we.title());
                String[] period = splitPeriod(we.period());
                e.setStartDate(period[0]);
                e.setEndDate(period[1]);
                e.setDescription(we.description());
                workMapper.insert(e);
            }
        }

        if (parsed.projectExperiences() != null) {
            for (ProjectExperience pe : parsed.projectExperiences()) {
                if (pe == null) {
                    continue;
                }
                ProjectExperienceEntity e = new ProjectExperienceEntity();
                e.setResumeId(resumeId);
                e.setName(pe.name());
                e.setRole(pe.role());
                String[] period = splitPeriod(pe.period());
                e.setStartDate(period[0]);
                e.setEndDate(period[1]);
                e.setDescription(pe.description());
                projectMapper.insert(e);

                if (pe.highlights() != null) {
                    for (int i = 0; i < pe.highlights().size(); i++) {
                        String h = pe.highlights().get(i);
                        if (h == null || h.isBlank()) {
                            continue;
                        }
                        ProjectHighlightEntity he = new ProjectHighlightEntity();
                        he.setProjectId(e.getId());
                        he.setContent(h);
                        he.setSortOrder(i);
                        highlightMapper.insert(he);
                    }
                }
            }
        }
        log.debug(
                "简历经历拆行入库完成 resumeId={} work={} project={}",
                resumeId,
                parsed.workExperiences() == null ? 0 : parsed.workExperiences().size(),
                parsed.projectExperiences() == null ? 0 : parsed.projectExperiences().size());
    }

    @Override
    @Transactional
    public void deleteByResumeId(Long resumeId) {
        if (resumeId == null) {
            return;
        }
        // 项目表删除会级联删除项目亮点
        workMapper.delete(
                Wrappers.<WorkExperienceEntity>lambdaQuery()
                        .eq(WorkExperienceEntity::getResumeId, resumeId));
        projectMapper.delete(
                Wrappers.<ProjectExperienceEntity>lambdaQuery()
                        .eq(ProjectExperienceEntity::getResumeId, resumeId));
    }

    /** 拆分 period "2020.06 - 2022.05" → [start, end]；无分隔时 end=null。 */
    private static String[] splitPeriod(String period) {
        if (period == null || period.isBlank()) {
            return new String[] {null, null};
        }
        int idx = period.indexOf('-');
        if (idx < 0) {
            return new String[] {period.trim(), null};
        }
        String start = period.substring(0, idx).trim();
        String end = period.substring(idx + 1).trim();
        return new String[] {start.isEmpty() ? null : start, end.isEmpty() ? null : end};
    }
}
