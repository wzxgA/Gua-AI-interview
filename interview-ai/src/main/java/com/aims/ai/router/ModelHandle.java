package com.aims.ai.router;

import com.aims.ai.config.AiModelProperties;
import java.util.concurrent.Semaphore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 模型句柄：一个档位（{@link ModelTier}）的完整运行时装配。
 *
 * <p>启动时由 ChatClientConfig 预构建并缓存于 {@link ModelRouter}，之后 {@code resolve()} 为 O(1) 纯内存操作。
 */
public final class ModelHandle {

    private final ModelTier tier;
    private final AiModelProperties.TierConfig config;
    private final ChatClient chatClient;
    private final ChatClient fallbackClient;
    private final String fallbackModel;
    private final EmbeddingModel embeddingModel;
    private final Semaphore concurrencyGate;

    private ModelHandle(
            ModelTier tier,
            AiModelProperties.TierConfig config,
            ChatClient chatClient,
            ChatClient fallbackClient,
            String fallbackModel,
            EmbeddingModel embeddingModel,
            Semaphore concurrencyGate) {
        this.tier = tier;
        this.config = config;
        this.chatClient = chatClient;
        this.fallbackClient = fallbackClient;
        this.fallbackModel = fallbackModel;
        this.embeddingModel = embeddingModel;
        this.concurrencyGate = concurrencyGate;
    }

    /** 构建对话类档位句柄（FLAGSHIP/STANDARD/ECONOMY）。 */
    public static ModelHandle chat(
            ModelTier tier,
            AiModelProperties.TierConfig config,
            ChatClient chatClient,
            ChatClient fallbackClient,
            String fallbackModel,
            Semaphore concurrencyGate) {
        return new ModelHandle(
                tier, config, chatClient, fallbackClient, fallbackModel, null, concurrencyGate);
    }

    /** 构建 Embedding 档位句柄（P1 仅装配不调用，P2 RAG 使用）。 */
    public static ModelHandle embedding(
            ModelTier tier,
            AiModelProperties.TierConfig config,
            EmbeddingModel embeddingModel,
            Semaphore concurrencyGate) {
        return new ModelHandle(tier, config, null, null, null, embeddingModel, concurrencyGate);
    }

    public ModelTier tier() {
        return tier;
    }

    public AiModelProperties.TierConfig config() {
        return config;
    }

    public ChatClient chatClient() {
        return chatClient;
    }

    public ChatClient fallbackClient() {
        return fallbackClient;
    }

    public String fallbackModel() {
        return fallbackModel;
    }

    public EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    public boolean hasFallback() {
        return fallbackClient != null;
    }

    /** 每 provider 的并发闸口（P7 配额管控扩展点，P1 仅预留）。 */
    public Semaphore concurrencyGate() {
        return concurrencyGate;
    }
}
