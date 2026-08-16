package com.aims.ai.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aims.ai.config.AiModelProperties;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.AiException;
import com.aims.core.common.exception.AiOutputParseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/** ModelRouter 路由与降级逻辑单测。 */
class ModelRouterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private ModelHandle handleWithFallback(ChatClient primary, ChatClient fallback) {
        AiModelProperties.TierConfig config =
                new AiModelProperties.TierConfig(
                        "dashscope",
                        "qwen-max",
                        0.7,
                        2048,
                        null,
                        "deepseek:deepseek-chat",
                        null,
                        null);
        return ModelHandle.chat(
                ModelTier.FLAGSHIP, config, primary, fallback, "deepseek-chat", new Semaphore(32));
    }

    private ModelHandle handleWithoutFallback(ChatClient primary) {
        AiModelProperties.TierConfig config =
                new AiModelProperties.TierConfig(
                        "deepseek", "deepseek-chat", 0.2, 2048, null, null, null, null);
        return ModelHandle.chat(ModelTier.STANDARD, config, primary, null, null, new Semaphore(32));
    }

    @Test
    void embeddingDimensionIsValidated() {
        org.springframework.ai.embedding.EmbeddingModel embeddingModel =
                mock(org.springframework.ai.embedding.EmbeddingModel.class);
        AiModelProperties.TierConfig config =
                new AiModelProperties.TierConfig(
                        "dashscope", "text-embedding-v4", null, null, 2048, null, null, null);
        ModelHandle handle =
                ModelHandle.embedding(
                        ModelTier.EMBEDDING, config, embeddingModel, new Semaphore(32));
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.EMBEDDING, handle), ModelTier.EMBEDDING, meterRegistry);
        when(embeddingModel.embed("text")).thenReturn(new float[1024]);

        AiException exception = assertThrows(AiException.class, () -> router.embed("text"));

        assertEquals(ErrorCode.EMBEDDING_FAILED, exception.getErrorCode());
    }

    @Test
    void embeddingBatchValidatesCountAndDimensions() {
        org.springframework.ai.embedding.EmbeddingModel embeddingModel =
                mock(org.springframework.ai.embedding.EmbeddingModel.class);
        AiModelProperties.TierConfig config =
                new AiModelProperties.TierConfig(
                        "dashscope", "text-embedding-v4", null, null, 2048, null, null, null);
        ModelHandle handle =
                ModelHandle.embedding(
                        ModelTier.EMBEDDING, config, embeddingModel, new Semaphore(32));
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.EMBEDDING, handle), ModelTier.EMBEDDING, meterRegistry);
        when(embeddingModel.embed(List.of("a", "b"))).thenReturn(List.of(new float[2048]));

        AiException exception =
                assertThrows(AiException.class, () -> router.embedBatch(List.of("a", "b")));

        assertEquals(ErrorCode.EMBEDDING_FAILED, exception.getErrorCode());
    }

    @Test
    void resolveReturnsConfiguredHandle() {
        ModelHandle handle = handleWithoutFallback(mock(ChatClient.class));
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.STANDARD, handle), ModelTier.STANDARD, meterRegistry);

        assertEquals(handle, router.resolve(ModelTier.STANDARD));
        assertEquals(handle, router.resolveDefault());
    }

    @Test
    void resolveUnsupportedTierThrows() {
        ModelRouter router = new ModelRouter(Map.of(), ModelTier.STANDARD, meterRegistry);

        AiException e = assertThrows(AiException.class, () -> router.resolve(ModelTier.FLAGSHIP));
        assertEquals(ErrorCode.MODEL_TIER_UNSUPPORTED, e.getErrorCode());
    }

    @Test
    void executeCallReturnsPrimaryResult() {
        ChatClient primary = mock(ChatClient.class);
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.STANDARD, handleWithoutFallback(primary)),
                        ModelTier.STANDARD,
                        meterRegistry);

        String result = router.executeCall(ModelTier.STANDARD, (client, model) -> "ok:" + model);

        assertEquals("ok:deepseek-chat", result);
    }

    @Test
    void executeCallFallsBackWhenPrimaryFails() {
        ChatClient primary = mock(ChatClient.class);
        ChatClient fallback = mock(ChatClient.class);
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.FLAGSHIP, handleWithFallback(primary, fallback)),
                        ModelTier.FLAGSHIP,
                        meterRegistry);

        String result =
                router.executeCall(
                        ModelTier.FLAGSHIP,
                        (client, model) -> {
                            if (client == primary) {
                                throw new RuntimeException("primary down");
                            }
                            return "fallback-ok:" + model;
                        });

        assertEquals("fallback-ok:deepseek-chat", result);
        assertEquals(
                1.0,
                meterRegistry
                        .counter(
                                "aims.model.fallback",
                                "tier",
                                "FLAGSHIP",
                                "from",
                                "qwen-max",
                                "to",
                                "deepseek-chat")
                        .count());
    }

    @Test
    void executeCallThrowsWhenNoFallbackConfigured() {
        ChatClient primary = mock(ChatClient.class);
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.STANDARD, handleWithoutFallback(primary)),
                        ModelTier.STANDARD,
                        meterRegistry);

        AiException e =
                assertThrows(
                        AiException.class,
                        () ->
                                router.executeCall(
                                        ModelTier.STANDARD,
                                        (client, model) -> {
                                            throw new RuntimeException("down");
                                        }));
        assertEquals(ErrorCode.MODEL_CALL_FAILED, e.getErrorCode());
    }

    @Test
    void executeCallThrowsWhenPrimaryAndFallbackBothFail() {
        ChatClient primary = mock(ChatClient.class);
        ChatClient fallback = mock(ChatClient.class);
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.FLAGSHIP, handleWithFallback(primary, fallback)),
                        ModelTier.FLAGSHIP,
                        meterRegistry);

        AiException e =
                assertThrows(
                        AiException.class,
                        () ->
                                router.executeCall(
                                        ModelTier.FLAGSHIP,
                                        (client, model) -> {
                                            throw new RuntimeException("down");
                                        }));
        assertEquals(ErrorCode.MODEL_CALL_FAILED, e.getErrorCode());
        // 主异常被附加到 fallback 异常的 suppressed 中
        assertEquals(1, e.getCause().getSuppressed().length);
    }

    @Test
    void outputParseFailureDoesNotTriggerFallback() {
        ChatClient primary = mock(ChatClient.class);
        ChatClient fallback = mock(ChatClient.class);
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.FLAGSHIP, handleWithFallback(primary, fallback)),
                        ModelTier.FLAGSHIP,
                        meterRegistry);

        assertThrows(
                AiOutputParseException.class,
                () ->
                        router.executeCall(
                                ModelTier.FLAGSHIP,
                                (client, model) -> {
                                    throw new AiOutputParseException("bad json", null);
                                }));
        // 未产生降级计数
        assertEquals(
                0,
                meterRegistry.find("aims.model.fallback").counter() == null
                        ? 0
                        : meterRegistry.find("aims.model.fallback").counter().count());
    }

    @Test
    void executeStreamFallsBackOnError() {
        ChatClient primary = mock(ChatClient.class);
        ChatClient fallback = mock(ChatClient.class);
        ModelRouter router =
                new ModelRouter(
                        Map.of(ModelTier.FLAGSHIP, handleWithFallback(primary, fallback)),
                        ModelTier.FLAGSHIP,
                        meterRegistry);

        String result =
                router
                        .executeStream(
                                ModelTier.FLAGSHIP,
                                (client, model) -> {
                                    if (client == primary) {
                                        return reactor.core.publisher.Flux.error(
                                                new RuntimeException("stream down"));
                                    }
                                    return reactor.core.publisher.Flux.just("a", "b");
                                })
                        .collectList()
                        .block()
                        .stream()
                        .reduce("", String::concat);

        assertEquals("ab", result);
    }
}
