package com.aims.infra.persistence.service;

import com.aims.core.resume.ParsedResume;
import com.aims.core.resume.ProjectExperience;
import com.aims.core.resume.WorkExperience;
import com.aims.infra.persistence.entity.ProjectExperienceEntity;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.entity.WorkExperienceEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 简历摘要统一构建服务（v1.1-C，TD1）：收敛 InterviewController / InterviewWebSocketHandler /
 * StatePersistenceService / EvaluationServiceImpl / ReportServiceImpl 五处重复的 buildResumeSummary。
 *
 * <p>输出<b>结构化精简摘要</b>（姓名/职位/年限/技能/工作经历/项目经历），替代原"parsed_json 全文透传"，减少 Prompt 上下文占用。v1.1-C
 * §3.3：经历部分优先读经历表（拆表后 SSOT），经历表为空（旧数据/双写失败）时回退 parsed_json；头部字段（姓名/职位/年限/技能）仍来自
 * parsed_json；两者皆缺失时回退 rawText 截断。
 */
@Service
public class ResumeSummaryBuilder {

    private static final Logger log = LoggerFactory.getLogger(ResumeSummaryBuilder.class);

    /** rawText 回退截断长度。 */
    private static final int RAW_FALLBACK_MAX = 800;

    /** 单条经历描述截断长度。 */
    private static final int DESC_MAX = 80;

    private final ObjectMapper objectMapper;
    private final ResumeExperienceService experienceService;

    public ResumeSummaryBuilder(
            ObjectMapper objectMapper, ResumeExperienceService experienceService) {
        this.objectMapper = objectMapper;
        this.experienceService = experienceService;
    }

    /** 构建简历摘要；resume 为 null 或内容缺失时返回 "未提供"。 */
    public String build(ResumeEntity resume) {
        if (resume == null) {
            return "未提供";
        }
        ParsedResume parsed = tryParse(resume);
        List<WorkExperienceEntity> workRows = List.of();
        List<ProjectExperienceEntity> projectRows = List.of();
        if (resume.getId() != null) {
            workRows = experienceService.listWork(resume.getId());
            projectRows = experienceService.listProject(resume.getId());
        }
        boolean hasExperienceRows = !workRows.isEmpty() || !projectRows.isEmpty();

        if (parsed != null || hasExperienceRows) {
            String structured = buildStructured(parsed, workRows, projectRows);
            if (!structured.isBlank()) {
                return structured;
            }
        }
        String rawText = resume.getRawText();
        if (rawText == null || rawText.isBlank()) {
            return "未提供";
        }
        return rawText.length() > RAW_FALLBACK_MAX
                ? rawText.substring(0, RAW_FALLBACK_MAX)
                : rawText;
    }

    private ParsedResume tryParse(ResumeEntity resume) {
        String json = resume.getParsedJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ParsedResume.class);
        } catch (Exception e) {
            log.warn("简历 parsed_json 反序列化失败 resumeId={}，回退 rawText", resume.getId(), e);
            return null;
        }
    }

    private String buildStructured(
            ParsedResume p,
            List<WorkExperienceEntity> workRows,
            List<ProjectExperienceEntity> projectRows) {
        StringBuilder sb = new StringBuilder();
        if (p != null) {
            if (notBlank(p.candidateName())) {
                sb.append("姓名：").append(p.candidateName()).append('\n');
            }
            if (notBlank(p.currentTitle())) {
                sb.append("当前职位：").append(p.currentTitle()).append('\n');
            }
            if (p.yearsOfExperience() != null) {
                sb.append("工作年限：").append(p.yearsOfExperience()).append(" 年\n");
            }
            appendSkills(sb, p.skills());
        }
        if (!workRows.isEmpty()) {
            appendWorkRows(sb, workRows);
        } else if (p != null) {
            appendWorkExperiences(sb, p.workExperiences());
        }
        if (!projectRows.isEmpty()) {
            appendProjectRows(sb, projectRows);
        } else if (p != null) {
            appendProjectExperiences(sb, p.projectExperiences());
        }
        return sb.toString();
    }

    private void appendSkills(StringBuilder sb, List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return;
        }
        sb.append("技能：").append(String.join("、", skills)).append('\n');
    }

    /** 经历表数据源（v1.1-C §3.3）。 */
    private void appendWorkRows(StringBuilder sb, List<WorkExperienceEntity> rows) {
        sb.append("工作经历：\n");
        for (WorkExperienceEntity w : rows) {
            sb.append("- ")
                    .append(nz(w.getCompany()))
                    .append(' ')
                    .append(nz(w.getPosition()))
                    .append(periodSuffix(w.getStartDate(), w.getEndDate()))
                    .append(descSuffix(w.getDescription()))
                    .append('\n');
        }
    }

    /** 经历表数据源（v1.1-C §3.3）。 */
    private void appendProjectRows(StringBuilder sb, List<ProjectExperienceEntity> rows) {
        sb.append("项目经历：\n");
        for (ProjectExperienceEntity p : rows) {
            sb.append("- ")
                    .append(nz(p.getName()))
                    .append(' ')
                    .append(nz(p.getRole()))
                    .append(periodSuffix(p.getStartDate(), p.getEndDate()))
                    .append('\n');
        }
    }

    /** parsed_json 回退数据源（经历表为空的旧数据）。 */
    private void appendWorkExperiences(StringBuilder sb, List<WorkExperience> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        sb.append("工作经历：\n");
        for (WorkExperience w : list) {
            if (w == null) {
                continue;
            }
            sb.append("- ")
                    .append(nz(w.company()))
                    .append(' ')
                    .append(nz(w.title()))
                    .append(periodSuffix(w.period()))
                    .append(descSuffix(w.description()))
                    .append('\n');
        }
    }

    /** parsed_json 回退数据源（经历表为空的旧数据）。 */
    private void appendProjectExperiences(StringBuilder sb, List<ProjectExperience> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        sb.append("项目经历：\n");
        for (ProjectExperience p : list) {
            if (p == null) {
                continue;
            }
            sb.append("- ")
                    .append(nz(p.name()))
                    .append(' ')
                    .append(nz(p.role()))
                    .append(periodSuffix(p.period()))
                    .append('\n');
        }
    }

    private static String periodSuffix(String startDate, String endDate) {
        if (notBlank(startDate) && notBlank(endDate)) {
            return "（" + startDate + " - " + endDate + "）";
        }
        if (notBlank(startDate)) {
            return "（" + startDate + "）";
        }
        if (notBlank(endDate)) {
            return "（" + endDate + "）";
        }
        return "";
    }

    private static String periodSuffix(String period) {
        return notBlank(period) ? "（" + period + "）" : "";
    }

    private static String descSuffix(String desc) {
        if (!notBlank(desc)) {
            return "";
        }
        String d = desc.length() <= DESC_MAX ? desc : desc.substring(0, DESC_MAX) + "...";
        return "：" + d;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
