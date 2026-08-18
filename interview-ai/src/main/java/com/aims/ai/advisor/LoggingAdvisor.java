package com.aims.ai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 调用日志 Advisor（顺序 100，最外层）。
 *
 * <p>记录 tier / model / prompt 摘要（截断 500 字符）/ 耗时；<b>不</b>记录完整 prompt， 防止敏感信息泄漏到日志采集。完整 prompt 落库审计留待
 * P4。
 */
public class LoggingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LoggingAdvisor.class);
    private static final int PROMPT_SUMMARY_MAX_DEFAULT = 200;

    /** prompt 摘要最大字符数（可由 AIMS_LOG_PROMPT_MAX_CHARS 覆盖，排查时调大）。 */
    private final int promptSummaryMax;

    public LoggingAdvisor() {
        this(PROMPT_SUMMARY_MAX_DEFAULT);
    }

    public LoggingAdvisor(int promptSummaryMax) {
        this.promptSummaryMax = promptSummaryMax;
    }

    @Override
    public String getName() {
        return "LoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        log.info(
                "llm.call tier={} model={} prompt={}",
                tier(request),
                model(request),
                summarize(request));
        try {
            ChatClientResponse response = chain.nextCall(request);
            log.info(
                    "llm.done tier={} model={} latency={}ms",
                    tier(request),
                    model(request),
                    System.currentTimeMillis() - start);
            return response;
        } catch (Exception e) {
            log.warn(
                    "llm.error tier={} model={} latency={}ms err={}",
                    tier(request),
                    model(request),
                    System.currentTimeMillis() - start,
                    e.toString());
            throw e;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain chain) {
        long start = System.currentTimeMillis();
        log.info(
                "llm.stream.start tier={} model={} prompt={}",
                tier(request),
                model(request),
                summarize(request));
        return chain.nextStream(request)
                .doOnComplete(
                        () ->
                                log.info(
                                        "llm.stream.done tier={} model={} latency={}ms",
                                        tier(request),
                                        model(request),
                                        System.currentTimeMillis() - start))
                .doOnError(
                        e ->
                                log.warn(
                                        "llm.stream.error tier={} model={} latency={}ms err={}",
                                        tier(request),
                                        model(request),
                                        System.currentTimeMillis() - start,
                                        e.toString()));
    }

    private String tier(ChatClientRequest request) {
        Object tier = request.context().get(AiAdvisorContext.TIER);
        return tier == null ? "-" : tier.toString();
    }

    private String model(ChatClientRequest request) {
        Object model = request.context().get(AiAdvisorContext.MODEL);
        return model == null ? "-" : model.toString();
    }

    /** prompt 摘要：截断 + 去换行；脱敏规则钩子预留（手机号/身份证等 P6 内容安全时实现）。 */
    private String summarize(ChatClientRequest request) {
        String content = request.prompt().getContents();
        if (content == null) {
            return "-";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= promptSummaryMax
                ? flat
                : flat.substring(0, promptSummaryMax) + "...(" + flat.length() + " chars)";
    }
}
