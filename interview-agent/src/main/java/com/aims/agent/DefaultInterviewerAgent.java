package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.InterviewerPersona;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 默认面试官实现：经 {@link AiChatFacade} 调用 FLAGSHIP 模型生成问题。
 *
 * <p>Prompt 构建委托给 {@link InterviewPromptBuilder}，本类只负责 AI 调用和结果校验。
 */
@Service
public class DefaultInterviewerAgent implements InterviewerAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultInterviewerAgent.class);

    /** 兜底问题池（AI 返回空或无计划时使用） */
    private static final List<String> FALLBACK_QUESTIONS =
            List.of(
                    "请先做一个简单的自我介绍。",
                    "请介绍一个你最有成就感的项目。",
                    "你在技术选型时最看重哪些因素？",
                    "请描述一个你遇到过的技术难题及解决过程。",
                    "你如何保持对新技术的学习？");

    /** 空结果最大重试次数 */
    private static final int MAX_RETRIES = 2;

    /** 重试间隔（毫秒） */
    private static final long RETRY_DELAY_MS = 500;

    private final AiChatFacade aiChatFacade;

    public DefaultInterviewerAgent(AiChatFacade aiChatFacade) {
        this.aiChatFacade = aiChatFacade;
    }

    @Override
    public String nextQuestion(InterviewContext context) {
        if (context.plan() == null || context.currentRound() > context.totalRounds()) {
            return pickFallback(context.recentQuestions());
        }
        InterviewerPersona persona =
                context.persona() != null ? context.persona() : InterviewerPersona.FRIENDLY;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            String result =
                    aiChatFacade.call(
                            ModelTier.FLAGSHIP,
                            InterviewPromptBuilder.interviewerSystem(persona),
                            InterviewPromptBuilder.interviewerUser(context));
            if (result != null && !result.isBlank()) {
                return result.trim();
            }
            log.warn("面试官生成问题为空，attempt={}/{}", attempt + 1, MAX_RETRIES + 1);
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.warn("AI 返回空已达最大重试次数，使用兜底问题");
        return pickFallback(context.recentQuestions());
    }

    @Override
    public Flux<String> streamQuestion(InterviewContext context) {
        if (context.plan() == null || context.currentRound() > context.totalRounds()) {
            return Flux.just(pickFallback(context.recentQuestions()));
        }
        InterviewerPersona persona =
                context.persona() != null ? context.persona() : InterviewerPersona.FRIENDLY;
        return aiChatFacade.stream(
                        ModelTier.FLAGSHIP,
                        InterviewPromptBuilder.interviewerSystem(persona),
                        InterviewPromptBuilder.interviewerUser(context))
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .switchIfEmpty(
                        Flux.defer(
                                () -> {
                                    log.warn("流式面试官生成问题为空，使用兜底问题");
                                    return Flux.just(pickFallback(context.recentQuestions()));
                                }));
    }

    /** 从兜底问题池选一个，排除已问过的。若全部已问过则允许重复。 */
    private String pickFallback(List<String> recentQuestions) {
        List<String> available =
                FALLBACK_QUESTIONS.stream()
                        .filter(q -> recentQuestions == null || !recentQuestions.contains(q))
                        .toList();
        List<String> pool = available.isEmpty() ? FALLBACK_QUESTIONS : available;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
