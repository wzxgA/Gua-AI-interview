package com.aims.infra.persistence.service;

import com.aims.core.resume.ParsedResume;
import com.aims.infra.persistence.entity.ProjectExperienceEntity;
import com.aims.infra.persistence.entity.WorkExperienceEntity;
import java.util.List;

/**
 * 简历经历拆行入库服务（v1.1-C）：把 {@code ParsedResume} 中的结构化经历同步到经历表。
 *
 * <p>职责：经历表的全量重建（删旧插新，幂等）与按简历删除/查询。供简历解析成功、人工修改后调用，保证经历表与 {@code resume.parsed_json} 双写一致。
 */
public interface ResumeExperienceService {

    /**
     * 按 {@code parsed} 全量重建该简历的经历行（工作/项目/项目亮点）。
     *
     * @param resumeId 简历 ID
     * @param parsed 结构化简历（可为 null，null 时仅清空经历行）
     */
    void syncFromParsed(Long resumeId, ParsedResume parsed);

    /** 按简历删除其全部经历行（项目删除级联项目亮点），简历删除时调用。 */
    void deleteByResumeId(Long resumeId);

    /** 查询该简历的工作经历行（按插入顺序）。 */
    List<WorkExperienceEntity> listWork(Long resumeId);

    /** 查询该简历的项目经历行（按插入顺序）。 */
    List<ProjectExperienceEntity> listProject(Long resumeId);
}
