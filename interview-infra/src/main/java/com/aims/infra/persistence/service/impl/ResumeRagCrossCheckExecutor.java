package com.aims.infra.persistence.service.impl;

import com.aims.agent.ResumeCrossCheckExecutor;
import com.aims.agent.ResumeCrossCheckResult;
import com.aims.infra.persistence.dto.RagSearchResponse;
import com.aims.infra.persistence.entity.ResumeSearchResult;
import com.aims.infra.persistence.service.ResumeRagService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ResumeCrossCheckExecutor} 的 RAG 实现：复用 {@link ResumeRagService} 混合检索（B §6）， 将 {@link
 * ResumeSearchResult} 可解释性字段映射为交叉验证证据。检索失败返回 null（不阻断追问决策）。
 */
@Component
public class ResumeRagCrossCheckExecutor implements ResumeCrossCheckExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResumeRagCrossCheckExecutor.class);

    private final ResumeRagService resumeRagService;

    public ResumeRagCrossCheckExecutor(ResumeRagService resumeRagService) {
        this.resumeRagService = resumeRagService;
    }

    @Override
    public ResumeCrossCheckResult crossCheck(Long candidateResumeId, String answerText) {
        if (candidateResumeId == null || answerText == null || answerText.isBlank()) {
            return null;
        }
        try {
            RagSearchResponse<ResumeSearchResult> response =
                    resumeRagService.search(answerText, candidateResumeId, 1, null);
            if (response == null || response.results() == null || response.results().isEmpty()) {
                return null;
            }
            ResumeSearchResult r = response.results().get(0);
            // F2 可观测：工具被调用 + 证据（分数/召回来源/命中片段）一眼可见，配合"追问决策完成 type=CLARIFY"闭环
            log.info(
                    "简历交叉验证 resumeId={} score={} recallSource={} snippet={}",
                    candidateResumeId,
                    r.score(),
                    r.recallSource(),
                    truncate(r.matchedSnippet(), 80));
            return new ResumeCrossCheckResult(
                    r.candidateName(),
                    r.score(),
                    r.keywordScore(),
                    r.matchedSnippet(),
                    r.matchedTerms() == null ? List.of() : r.matchedTerms(),
                    r.matchedFields() == null ? List.of() : r.matchedFields(),
                    r.recallSource());
        } catch (Exception e) {
            log.warn("简历交叉验证检索失败 resumeId={} err={}", candidateResumeId, e.getMessage());
            return null;
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "-";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
