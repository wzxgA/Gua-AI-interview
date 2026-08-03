package com.aims.core.interview;

import java.util.List;

/** 追问上下文，传递给 FollowUpAgent。 */
public record FollowUpContext(
        Long sessionId,
        Long roundId,
        String question,
        String answer,
        String candidateName,
        String positionTitle,
        String jdText,
        String resumeSummary,
        List<String> followUpHints,
        List<String> recentQuestions,
        InterviewerPersona persona) {

    public FollowUpContext {
        followUpHints = followUpHints == null ? List.of() : List.copyOf(followUpHints);
        recentQuestions = recentQuestions == null ? List.of() : List.copyOf(recentQuestions);
        persona = persona == null ? InterviewerPersona.FRIENDLY : persona;
    }
}
