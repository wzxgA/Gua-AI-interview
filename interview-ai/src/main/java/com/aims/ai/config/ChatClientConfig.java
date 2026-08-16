package com.aims.ai.config;

import com.aims.ai.advisor.LoggingAdvisor;
import com.aims.ai.advisor.RetryAdvisor;
import com.aims.ai.advisor.TokenMeterAdvisor;
import com.aims.ai.router.ModelHandle;
import com.aims.ai.router.ModelRouter;
import com.aims.ai.router.ModelTier;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 装配：启动时按 aims.ai 配置预构建全部档位句柄并缓存（不可变 Map）， 之后 {@link ModelRouter#resolve} 为 O(1) 纯内存操作。
 *
 * <p>每个 provider 构建一个 {@link OpenAiApi}；每个档位构建独立 {@link OpenAiChatOptions} 缓存复用，不每次新建。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiModelProperties.class)
public class ChatClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatClientConfig.class);

    @Bean
    LoggingAdvisor loggingAdvisor() {
        return new LoggingAdvisor();
    }

    @Bean
    TokenMeterAdvisor tokenMeterAdvisor(MeterRegistry meterRegistry, AiModelProperties properties) {
        return new TokenMeterAdvisor(meterRegistry, properties);
    }

    @Bean
    RetryAdvisor retryAdvisor(AiModelProperties properties) {
        AiModelProperties.RetryPolicy retry = properties.retryOrDefault();
        return new RetryAdvisor(retry.effectiveMaxAttempts(), retry.effectiveInitialBackoff());
    }

    @Bean
    ModelRouter modelRouter(
            AiModelProperties properties,
            MeterRegistry meterRegistry,
            LoggingAdvisor loggingAdvisor,
            TokenMeterAdvisor tokenMeterAdvisor,
            RetryAdvisor retryAdvisor) {
        // 1. 每 provider 构建一个 OpenAiApi（不同 base-url / api-key）
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

        // 2. 每档位预构建句柄
        Map<ModelTier, ModelHandle> handles = new EnumMap<>(ModelTier.class);
        properties
                .tiers()
                .forEach(
                        (tier, tierConfig) ->
                                handles.put(
                                        tier,
                                        buildHandle(
                                                tier,
                                                tierConfig,
                                                properties,
                                                apis,
                                                loggingAdvisor,
                                                tokenMeterAdvisor,
                                                retryAdvisor)));

        log.info("模型路由装配完成 tiers={} default={}", handles.keySet(), properties.defaultTier());
        return new ModelRouter(handles, properties.defaultTier(), meterRegistry);
    }

    private ModelHandle buildHandle(
            ModelTier tier,
            AiModelProperties.TierConfig tierConfig,
            AiModelProperties properties,
            Map<String, OpenAiApi> apis,
            LoggingAdvisor loggingAdvisor,
            TokenMeterAdvisor tokenMeterAdvisor,
            RetryAdvisor retryAdvisor) {
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
        // 三个基础 Advisor 作为默认 Advisor 装配进每个档位的 ChatClient，业务调用零感知
        return ChatClient.builder(chatModel)
                .defaultAdvisors(loggingAdvisor, tokenMeterAdvisor, retryAdvisor)
                .build();
    }
}
