package com.aims.infra.persistence.service.impl;

import com.aims.agent.ConflictDetail;
import com.aims.agent.ResumeCrossCheckExecutor;
import com.aims.agent.ResumeCrossCheckResult;
import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.ProjectExperienceEntity;
import com.aims.infra.persistence.entity.ResumeSearchResult;
import com.aims.infra.persistence.entity.WorkExperienceEntity;
import com.aims.infra.persistence.mapper.ProjectExperienceMapper;
import com.aims.infra.persistence.mapper.WorkExperienceMapper;
import com.aims.infra.persistence.service.ResumeRagService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ResumeCrossCheckExecutor} 实现（F2 + F3）。
 *
 * <p>F3 实体级比对：优先从经历表（resume_work_experience / resume_project_experience）按公司/项目名精确比对，产出 {@link
 * ConflictDetail} 矛盾点明细（一致 / 简历未提及 / 时间线不一致）；无实体可判时回退 F2 的 {@link ResumeRagService}
 * 混合检索（行为不劣化）。检索/比对异常返回 null（不阻断追问决策）。
 */
@Component
public class ResumeRagCrossCheckExecutor implements ResumeCrossCheckExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResumeRagCrossCheckExecutor.class);

    private static final int SNIPPET_MAX = 80;

    /** 一致时参考分。 */
    private static final double CONSISTENT_SCORE = 0.9;

    /** 矛盾时参考分（低分，配合 conflictDetails）。 */
    private static final double CONFLICT_SCORE = 0.3;

    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2}|19\\d{2})");

    private final ResumeRagService resumeRagService;
    private final WorkExperienceMapper workMapper;
    private final ProjectExperienceMapper projectMapper;

    public ResumeRagCrossCheckExecutor(
            ResumeRagService resumeRagService,
            WorkExperienceMapper workMapper,
            ProjectExperienceMapper projectMapper) {
        this.resumeRagService = resumeRagService;
        this.workMapper = workMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public ResumeCrossCheckResult crossCheck(
            Long candidateResumeId, String answerText, String companyHint) {
        if (candidateResumeId == null || answerText == null || answerText.isBlank()) {
            return null;
        }
        try {
            // 1) F3 实体级比对：命中经历表实体则精确判定（一致 / 未提及 / 时间冲突）
            EntityMatch entityMatch = entityCompare(candidateResumeId, answerText, companyHint);
            if (entityMatch != null) {
                log.info(
                        "简历交叉验证(实体级) resumeId={} conflicts={} score={} snippet={}",
                        candidateResumeId,
                        entityMatch.details().size(),
                        entityMatch.score(),
                        truncate(entityMatch.snippet(), SNIPPET_MAX));
                return new ResumeCrossCheckResult(
                        null,
                        entityMatch.score(),
                        entityMatch.keywordScore(),
                        entityMatch.snippet(),
                        List.of(),
                        entityMatch.fields(),
                        "ENTITY",
                        entityMatch.details());
            }

            // 2) 回退 F2 全文检索（无实体可判）
            return ragFallback(candidateResumeId, answerText);
        } catch (Exception e) {
            log.warn("简历交叉验证失败 resumeId={} err={}", candidateResumeId, e.getMessage());
            return null;
        }
    }

    // ---- F3 实体级比对 ----

    /**
     * 实体级比对：从回答（+companyHint）收集候选实体（公司/项目名），与经历表精确比对。
     *
     * @return 有候选实体可判时返回比对结果；无候选实体（companyHint 空且回答未命中任何经历实体）返回 null 触发回退
     */
    private EntityMatch entityCompare(Long resumeId, String answerText, String companyHint) {
        List<WorkExperienceEntity> works = listWork(resumeId);
        List<ProjectExperienceEntity> projects = listProject(resumeId);

        // 收集候选实体：companyHint + 回答文本中出现的经历表公司/项目名（忽略大小写）
        List<ConflictDetail> details = new ArrayList<>();
        String matchedSnippet = null;
        String matchedField = null;
        boolean anyCandidate = false;

        Set<String> companyCandidates = new LinkedHashSet<>();
        if (notBlank(companyHint)) {
            companyCandidates.add(companyHint.trim());
            anyCandidate = true;
        }
        for (WorkExperienceEntity w : works) {
            if (notBlank(w.getCompany()) && containsIgnoreCase(answerText, w.getCompany().trim())) {
                companyCandidates.add(w.getCompany().trim());
                anyCandidate = true;
            }
        }

        for (String company : companyCandidates) {
            WorkExperienceEntity hit = findWorkByCompany(works, company);
            if (hit == null) {
                details.add(
                        new ConflictDetail(
                                "company", null, company, snippetAround(answerText, company)));
            } else {
                matchedSnippet = chooseSnippet(matchedSnippet, hit.getDescription());
                matchedField = "work";
                List<Integer> years = extractYears(answerText);
                if (!years.isEmpty()) {
                    ConflictDetail period =
                            periodConflict(
                                    hit.getStartDate(),
                                    hit.getEndDate(),
                                    years,
                                    snippetAround(answerText, company));
                    if (period != null) {
                        details.add(period);
                    }
                }
            }
        }

        Set<String> projectCandidates = new LinkedHashSet<>();
        for (ProjectExperienceEntity p : projects) {
            if (notBlank(p.getName()) && containsIgnoreCase(answerText, p.getName().trim())) {
                projectCandidates.add(p.getName().trim());
                anyCandidate = true;
            }
        }
        for (String name : projectCandidates) {
            ProjectExperienceEntity hit = findProjectByName(projects, name);
            if (hit == null) {
                details.add(
                        new ConflictDetail("project", null, name, snippetAround(answerText, name)));
            } else {
                matchedSnippet = chooseSnippet(matchedSnippet, hit.getDescription());
                matchedField = "project";
                List<Integer> years = extractYears(answerText);
                if (!years.isEmpty()) {
                    ConflictDetail period =
                            periodConflict(
                                    hit.getStartDate(),
                                    hit.getEndDate(),
                                    years,
                                    snippetAround(answerText, name));
                    if (period != null) {
                        details.add(period);
                    }
                }
            }
        }

        if (!anyCandidate) {
            return null;
        }
        List<String> fields = matchedField == null ? List.of() : List.of(matchedField);
        return new EntityMatch(details, matchedSnippet, fields);
    }

    private List<WorkExperienceEntity> listWork(Long resumeId) {
        return workMapper.selectList(
                Wrappers.<WorkExperienceEntity>lambdaQuery()
                        .eq(WorkExperienceEntity::getResumeId, resumeId));
    }

    private List<ProjectExperienceEntity> listProject(Long resumeId) {
        return projectMapper.selectList(
                Wrappers.<ProjectExperienceEntity>lambdaQuery()
                        .eq(ProjectExperienceEntity::getResumeId, resumeId));
    }

    private WorkExperienceEntity findWorkByCompany(
            List<WorkExperienceEntity> works, String company) {
        for (WorkExperienceEntity w : works) {
            if (notBlank(w.getCompany()) && w.getCompany().trim().equalsIgnoreCase(company)) {
                return w;
            }
        }
        return null;
    }

    private ProjectExperienceEntity findProjectByName(
            List<ProjectExperienceEntity> projects, String name) {
        for (ProjectExperienceEntity p : projects) {
            if (notBlank(p.getName()) && p.getName().trim().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /** 时间线冲突判定：回答中的年份区间与经历 start/end 年份区间完全不交叠才算冲突。 无法从回答提取年份时不判定（避免误报）。 */
    private ConflictDetail periodConflict(
            String startDate, String endDate, List<Integer> answerYears, String snippet) {
        Integer rs = yearOf(startDate);
        Integer re = yearOf(endDate);
        if (rs == null && re == null) {
            return null;
        }
        int er1 = rs != null ? rs : re;
        int er2 = re != null ? re : rs;
        int ay1 = Collections.min(answerYears);
        int ay2 = Collections.max(answerYears);
        boolean overlap = ay2 >= er1 && ay1 <= er2;
        if (overlap) {
            return null;
        }
        String expected = (rs == null ? "" : rs) + " - " + (re == null ? "" : re);
        return new ConflictDetail("period", expected.trim(), ay1 + " - " + ay2, snippet);
    }

    private Integer yearOf(String date) {
        if (date == null) {
            return null;
        }
        Matcher m = YEAR_PATTERN.matcher(date);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private List<Integer> extractYears(String text) {
        if (text == null) {
            return List.of();
        }
        List<Integer> years = new ArrayList<>();
        Matcher m = YEAR_PATTERN.matcher(text);
        while (m.find()) {
            years.add(Integer.parseInt(m.group(1)));
        }
        return years;
    }

    private static String chooseSnippet(String current, String candidate) {
        if (notBlank(current)) {
            return current;
        }
        return truncate(candidate, SNIPPET_MAX);
    }

    // ---- F2 全文检索回退 ----

    private ResumeCrossCheckResult ragFallback(Long candidateResumeId, String answerText) {
        RagSearchResponse<ResumeSearchResult> response =
                resumeRagService.search(answerText, candidateResumeId, 1, null);
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return null;
        }
        ResumeSearchResult r = response.results().get(0);
        log.info(
                "简历交叉验证(全文回退) resumeId={} score={} recallSource={} snippet={}",
                candidateResumeId,
                r.score(),
                r.recallSource(),
                truncate(r.matchedSnippet(), SNIPPET_MAX));
        return new ResumeCrossCheckResult(
                r.candidateName(),
                r.score(),
                r.keywordScore(),
                r.matchedSnippet(),
                r.matchedTerms() == null ? List.of() : r.matchedTerms(),
                r.matchedFields() == null ? List.of() : r.matchedFields(),
                r.recallSource(),
                List.of());
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private static String snippetAround(String text, String keyword) {
        if (text == null || keyword == null) {
            return null;
        }
        int idx = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return truncate(text, SNIPPET_MAX);
        }
        int from = Math.max(0, idx - 30);
        int to = Math.min(text.length(), idx + keyword.length() + 30);
        String prefix = from > 0 ? "…" : "";
        String suffix = to < text.length() ? "…" : "";
        return prefix + text.substring(from, to) + suffix;
    }

    private static boolean containsIgnoreCase(String text, String term) {
        return text != null && term != null && text.toLowerCase().contains(term.toLowerCase());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 实体级比对结果（executor 内部装配）。 */
    private record EntityMatch(List<ConflictDetail> details, String snippet, List<String> fields) {

        double score() {
            return details().isEmpty() ? CONSISTENT_SCORE : CONFLICT_SCORE;
        }

        double keywordScore() {
            return details().isEmpty() ? 1.0 : 0.0;
        }
    }
}
