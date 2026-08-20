package com.aims.agent;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 低成本正则提名（一级）：复包 {@link CompanyNameExtractor}，命中即提名，不做语义过滤。
 *
 * <p>作为 {@link AiResumeMentionExtractor} 的免费门：大多数轮次回答不提及公司，返回空，零 AI 调用。
 */
@Component
public class RegexResumeMentionExtractor implements ResumeEntityMentionExtractor {

    @Override
    public List<ResumeMention> extract(String answer) {
        String name = CompanyNameExtractor.extract(answer);
        if (name == null) {
            return List.of();
        }
        return List.of(new ResumeMention(name, name));
    }
}
