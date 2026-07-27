package com.aims.ai.advisor;

import com.aims.ai.config.AiModelProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import reactor.core.publisher.Flux;

/**
 * Token 计量 Advisor（顺序 200）。
 *
 * <p>指标：{@code aims.llm.tokens{tier,model,type}}（Counter）、{@code
 * aims.llm.latency{tier,model}}（Timer）、 {@code aims.llm.cost{tier,model}}（Counter，按价目表估算，租户 tag 预留
 * P7 填充）。 每次调用输出一行计量 INFO 日志。
 *
 * <p>R2 兼容：部分 OpenAI 兼容渠道的流式响应不返回 usage，做 null 容忍并告警，不中断调用。
 */
public class TokenMeterAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenMeterAdvisor.class);

    private final MeterRegistry meterRegistry;
    private final Map<String, AiModelProperties.Pricing> pricing;

    public TokenMeterAdvisor(MeterRegistry meterRegistry, AiModelProperties properties) {
        this.meterRegistry = meterRegistry;
        this.pricing = properties.pricing() == null ? Map.of() : properties.pricing();
    }

    @Override
    public String getName() {
        return "TokenMeterAdvisor";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);
        Usage usage =
                response.chatResponse() == null
                        ? null
                        : response.chatResponse().getMetadata().getUsage();
        record(request, usage, System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain chain) {
        long start = System.currentTimeMillis();
        // 流式 usage 通常在最后一个分片中（部分渠道完全不返回，见 R2）
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        return chain.nextStream(request)
                .doOnNext(
                        resp -> {
                            if (resp.chatResponse() != null
                                    && resp.chatResponse().getMetadata().getUsage() != null) {
                                lastUsage.set(resp.chatResponse().getMetadata().getUsage());
                            }
                        })
                .doOnComplete(
                        () -> record(request, lastUsage.get(), System.currentTimeMillis() - start));
    }

    private void record(ChatClientRequest request, Usage usage, long latencyMs) {
        String tier = tag(request, AiAdvisorContext.TIER);
        String model = tag(request, AiAdvisorContext.MODEL);

        meterRegistry
                .timer("aims.llm.latency", "tier", tier, "model", model)
                .record(latencyMs, TimeUnit.MILLISECONDS);

        if (usage == null || isEmpty(usage)) {
            log.warn(
                    "模型未返回 usage，跳过 token 计量 tier={} model={} latency={}ms",
                    tier,
                    model,
                    latencyMs);
            return;
        }

        long prompt = asLong(usage.getPromptTokens());
        long completion = asLong(usage.getCompletionTokens());
        meterRegistry
                .counter("aims.llm.tokens", "tier", tier, "model", model, "type", "prompt")
                .increment(prompt);
        meterRegistry
                .counter("aims.llm.tokens", "tier", tier, "model", model, "type", "completion")
                .increment(completion);

        String cost = estimateCost(model, prompt, completion);
        log.info(
                "tier={} model={} prompt={} completion={} cost={} latency={}ms",
                tier,
                model,
                prompt,
                completion,
                cost,
                latencyMs);
    }

    /** 按价目表（元/千 tokens）估算成本；无价目时返回 "-" 且不计指标。 */
    private String estimateCost(String model, long promptTokens, long completionTokens) {
        AiModelProperties.Pricing price = pricing.get(model);
        if (price == null || price.input() == null || price.output() == null) {
            return "-";
        }
        BigDecimal cost =
                price.input()
                        .multiply(BigDecimal.valueOf(promptTokens))
                        .add(price.output().multiply(BigDecimal.valueOf(completionTokens)))
                        .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        meterRegistry.counter("aims.llm.cost", "model", model).increment(cost.doubleValue());
        return cost.stripTrailingZeros().toPlainString();
    }

    /** usage 缺失判定：null 或全零（Spring AI 对无 usage 的响应填充 EmptyUsage）。 */
    private boolean isEmpty(Usage usage) {
        return asLong(usage.getPromptTokens()) == 0
                && asLong(usage.getCompletionTokens()) == 0
                && asLong(usage.getTotalTokens()) == 0;
    }

    private String tag(ChatClientRequest request, String key) {
        Object value = request.context().get(key);
        return value == null ? "unknown" : value.toString();
    }

    private long asLong(Object number) {
        return number instanceof Number n ? n.longValue() : 0L;
    }
}
