package com.aims.ai.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aims.ai.config.AiModelProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** TokenMeterAdvisor 计量提取与成本估算单测。 */
class TokenMeterAdvisorTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private AiModelProperties propertiesWithPricing() {
        return new AiModelProperties(
                com.aims.ai.router.ModelTier.STANDARD,
                Map.of(),
                Map.of(),
                Map.of(
                        "deepseek-chat",
                        new AiModelProperties.Pricing(
                                new BigDecimal("0.002"), new BigDecimal("0.008"))),
                null);
    }

    private ChatClientRequest request() {
        return ChatClientRequest.builder()
                .prompt(new Prompt("hello"))
                .context(
                        Map.of(
                                AiAdvisorContext.TIER,
                                "STANDARD",
                                AiAdvisorContext.MODEL,
                                "deepseek-chat"))
                .build();
    }

    private ChatClientResponse responseWithUsage(int promptTokens, int completionTokens) {
        ChatResponse chatResponse =
                new ChatResponse(
                        List.of(new Generation(new AssistantMessage("ok"))),
                        ChatResponseMetadata.builder()
                                .usage(new DefaultUsage(promptTokens, completionTokens))
                                .build());
        return ChatClientResponse.builder().chatResponse(chatResponse).build();
    }

    /** CallAdvisorChain 有三个抽象方法，无法使用 lambda，这里做最小桩实现。 */
    private CallAdvisorChain chainReturning(ChatClientResponse response) {
        return new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest req) {
                return response;
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }

            @Override
            public CallAdvisorChain copy(CallAdvisor callAdvisor) {
                return this;
            }
        };
    }

    @Test
    void recordsTokenCostAndLatencyMetrics() {
        TokenMeterAdvisor advisor = new TokenMeterAdvisor(meterRegistry, propertiesWithPricing());
        CallAdvisorChain chain = chainReturning(responseWithUsage(10, 20));

        advisor.adviseCall(request(), chain);

        assertEquals(
                10.0,
                meterRegistry
                        .counter(
                                "aims.llm.tokens",
                                "tier",
                                "STANDARD",
                                "model",
                                "deepseek-chat",
                                "type",
                                "prompt")
                        .count());
        assertEquals(
                20.0,
                meterRegistry
                        .counter(
                                "aims.llm.tokens",
                                "tier",
                                "STANDARD",
                                "model",
                                "deepseek-chat",
                                "type",
                                "completion")
                        .count());
        // 成本 = 0.002*10/1000 + 0.008*20/1000 = 0.00018 元
        assertEquals(
                0.00018,
                meterRegistry.counter("aims.llm.cost", "model", "deepseek-chat").count(),
                1e-9);
        assertEquals(
                1,
                meterRegistry
                        .timer("aims.llm.latency", "tier", "STANDARD", "model", "deepseek-chat")
                        .count());
    }

    @Test
    void toleratesMissingUsage() {
        TokenMeterAdvisor advisor = new TokenMeterAdvisor(meterRegistry, propertiesWithPricing());
        ChatClientResponse noUsage =
                ChatClientResponse.builder()
                        .chatResponse(
                                new ChatResponse(
                                        List.of(new Generation(new AssistantMessage("ok")))))
                        .build();
        CallAdvisorChain chain = chainReturning(noUsage);

        // 不抛异常，仅告警跳过（R2：部分渠道流式响应无 usage）
        advisor.adviseCall(request(), chain);

        assertTrue(meterRegistry.find("aims.llm.tokens").counter() == null);
        assertEquals(
                1,
                meterRegistry
                        .timer("aims.llm.latency", "tier", "STANDARD", "model", "deepseek-chat")
                        .count());
    }
}
