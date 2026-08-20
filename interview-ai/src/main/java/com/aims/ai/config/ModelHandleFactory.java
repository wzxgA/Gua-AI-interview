package com.aims.ai.config;

import com.aims.ai.advisor.LoggingAdvisor;
import com.aims.ai.advisor.RetryAdvisor;
import com.aims.ai.advisor.TokenMeterAdvisor;
import com.aims.ai.router.ModelHandle;
import com.aims.ai.router.ModelTier;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * 模型句柄无状态工厂：按 {@link AiModelProperties}（yml 与 DB 合并后的生效配置）构建全部档位句柄。
 *
 * <p>启动时由 {@link ChatClientConfig} 用于构建 {@code ModelRouter} 初始快照；运行时由 ModelRouter {@code refresh()}
 * 在配置变更后重建句柄（原子替换，在途请求不受影响）。
 */
@Component
public class ModelHandleFactory {

    private static final Logger log = LoggerFactory.getLogger(ModelHandleFactory.class);

    private final LoggingAdvisor loggingAdvisor;
    private final TokenMeterAdvisor tokenMeterAdvisor;
    private final RetryAdvisor retryAdvisor;

    public ModelHandleFactory(
            LoggingAdvisor loggingAdvisor,
            TokenMeterAdvisor tokenMeterAdvisor,
            RetryAdvisor retryAdvisor) {
        this.loggingAdvisor = loggingAdvisor;
        this.tokenMeterAdvisor = tokenMeterAdvisor;
        this.retryAdvisor = retryAdvisor;
    }

    /** 每个 provider 构建一个 {@link OpenAiApi}（不同 base-url / api-key）。 */
    public Map<String, OpenAiApi> buildApis(AiModelProperties properties) {
        Map<String, OpenAiApi> apis = new HashMap<>();
        properties
                .providers()
                .forEach(
                        (name, cfg) -> {
                            log.info(
                                    "装配 Provider: {} base-url={} key={}",
                                    name,
                                    cfg.baseUrl(),
                                    cfg.effectiveApiKey() != null
                                            ? cfg.effectiveApiKey()
                                                            .substring(
                                                                    0,
                                                                    Math.min(
                                                                            10,
                                                                            cfg.effectiveApiKey()
                                                                                    .length()))
                                                    + "***"
                                            : "null");
                            apis.put(
                                    name,
                                    OpenAiApi.builder()
                                            .baseUrl(cfg.baseUrl())
                                            .apiKey(cfg.effectiveApiKey())
                                            .build());
                        });
        return apis;
    }

    /** 每个档位预构建句柄（chat / embedding），返回不可变 Map。 */
    public Map<ModelTier, ModelHandle> buildHandles(
            AiModelProperties properties, Map<String, OpenAiApi> apis) {
        Map<ModelTier, ModelHandle> handles = new EnumMap<>(ModelTier.class);
        properties
                .tiers()
                .forEach(
                        (tier, tierConfig) ->
                                handles.put(tier, buildHandle(tier, tierConfig, properties, apis)));
        return Map.copyOf(handles);
    }

    private ModelHandle buildHandle(
            ModelTier tier,
            AiModelProperties.TierConfig tierConfig,
            AiModelProperties properties,
            Map<String, OpenAiApi> apis) {
        OpenAiApi api = apis.get(tierConfig.provider());
        if (api == null) {
            throw new IllegalStateException(
                    "aims.ai.tiers."
                            + tier
                            + ".provider 未在 providers 中定义: "
                            + tierConfig.provider());
        }
        AiModelProperties.ProviderConfig providerConfig =
                properties.providers().get(tierConfig.provider());
        Semaphore gate = new Semaphore(providerConfig.effectiveMaxConcurrency());

        if (tier == ModelTier.EMBEDDING) {
            EmbeddingModel embeddingModel =
                    new OpenAiEmbeddingModel(
                            api,
                            MetadataMode.EMBED,
                            OpenAiEmbeddingOptions.builder()
                                    .model(tierConfig.model())
                                    .dimensions(
                                            tierConfig.dimensions() == null
                                                    ? 2048
                                                    : tierConfig.dimensions())
                                    .build());
            return ModelHandle.embedding(tier, tierConfig, embeddingModel, gate);
        }

        ChatClient chatClient =
                buildChatClient(
                        api,
                        tierConfig.model(),
                        tierConfig,
                        loggingAdvisor,
                        tokenMeterAdvisor,
                        retryAdvisor);

        // 主备切换目标：fallback 形如 "provider:model"
        ChatClient fallbackClient = null;
        String fallbackModel = null;
        if (tierConfig.fallback() != null && !tierConfig.fallback().isBlank()) {
            String[] parts = tierConfig.fallback().split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalStateException(
                        "aims.ai.tiers."
                                + tier
                                + ".fallback 格式应为 provider:model，当前值: "
                                + tierConfig.fallback());
            }
            OpenAiApi fallbackApi = apis.get(parts[0]);
            if (fallbackApi == null) {
                throw new IllegalStateException(
                        "aims.ai.tiers."
                                + tier
                                + ".fallback 的 provider 未在 providers 中定义: "
                                + parts[0]);
            }
            fallbackModel = parts[1];
            fallbackClient =
                    buildChatClient(
                            fallbackApi,
                            fallbackModel,
                            tierConfig,
                            loggingAdvisor,
                            tokenMeterAdvisor,
                            retryAdvisor);
        }
        return ModelHandle.chat(tier, tierConfig, chatClient, fallbackClient, fallbackModel, gate);
    }

    private ChatClient buildChatClient(
            OpenAiApi api,
            String model,
            AiModelProperties.TierConfig tierConfig,
            LoggingAdvisor loggingAdvisor,
            TokenMeterAdvisor tokenMeterAdvisor,
            RetryAdvisor retryAdvisor) {
        OpenAiChatOptions.Builder optionsBuilder =
                OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(tierConfig.temperature())
                        .maxTokens(tierConfig.maxTokens());

        // DeepSeek 推理模型思考模式控制（OpenAI 兼容 extra_body）：
        //   thinking: enabled/disabled（或 true/false），留空则不设置走模型默认
        //   reasoning-effort: low/high/max（仅思考开启时生效）
        if (tierConfig.reasoningEffort() != null && !tierConfig.reasoningEffort().isBlank()) {
            optionsBuilder.reasoningEffort(tierConfig.reasoningEffort());
        }
        if (tierConfig.thinking() != null && !tierConfig.thinking().isBlank()) {
            boolean enabled =
                    tierConfig.thinking().equalsIgnoreCase("enabled")
                            || tierConfig.thinking().equalsIgnoreCase("true");
            optionsBuilder.extraBody(
                    Map.of("thinking", Map.of("type", enabled ? "enabled" : "disabled")));
        }

        OpenAiChatOptions options = optionsBuilder.build();
        ChatModel chatModel =
                OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
        return ChatClient.builder(chatModel)
                .defaultAdvisors(loggingAdvisor, tokenMeterAdvisor, retryAdvisor)
                .build();
    }
}
