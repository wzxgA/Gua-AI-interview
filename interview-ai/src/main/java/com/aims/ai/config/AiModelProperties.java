package com.aims.ai.config;

import com.aims.ai.router.ModelTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 多模型配置绑定（前缀 {@code aims.ai}）。杜绝散落的 {@code @Value}。
 *
 * <p>结构：providers（提供商接入信息）+ tiers（档位 -> 提供商/模型/参数映射）+ pricing（价目表）+ retry（重试策略）。
 */
@Validated
@ConfigurationProperties(prefix = "aims.ai")
public record AiModelProperties(
        @NotNull(message = "aims.ai.default-tier 未配置") ModelTier defaultTier,
        @NotEmpty(message = "aims.ai.providers 未配置") @Valid Map<String, ProviderConfig> providers,
        @NotEmpty(message = "aims.ai.tiers 未配置") @Valid Map<ModelTier, TierConfig> tiers,
        Map<String, Pricing> pricing,
        RetryPolicy retry) {

    /** 提供商接入配置（OpenAI 兼容协议：base-url + api-key 即可接入 Qwen/DeepSeek/GPT）。 */
    public record ProviderConfig(
            @NotBlank(message = "provider 的 base-url 不能为空") String baseUrl,
            @NotBlank(message = "provider 的 api-key 不能为空（请检查环境变量是否注入）") String apiKey,
            List<String> apiKeys,
            Integer maxConcurrency) {

        /** 并发闸口默认值 32（P7 配额管控扩展点）。 */
        public int effectiveMaxConcurrency() {
            return maxConcurrency == null || maxConcurrency <= 0 ? 32 : maxConcurrency;
        }

        /** API Key 轮询结构预留：P1 单 Key，取列表首项或 apiKey。 */
        public String effectiveApiKey() {
            return apiKeys != null && !apiKeys.isEmpty() ? apiKeys.getFirst() : apiKey;
        }
    }

    /** 档位配置：provider 引用 providers 的 key；fallback 形如 {@code provider:model}。 */
    public record TierConfig(
            @NotBlank String provider,
            @NotBlank String model,
            Double temperature,
            Integer maxTokens,
            Integer dimensions,
            String fallback,
            String thinking,
            String reasoningEffort) {}

    /** 价目表：元/千 tokens（成本估算用，Counter aims.llm.cost）。 */
    public record Pricing(BigDecimal input, BigDecimal output) {}

    /** 原地重试策略（RetryAdvisor）：默认指数退避 500ms * 2^n，最多重试 2 次。 */
    public record RetryPolicy(Integer maxAttempts, Duration initialBackoff) {

        public int effectiveMaxAttempts() {
            return maxAttempts == null || maxAttempts < 0 ? 2 : maxAttempts;
        }

        public Duration effectiveInitialBackoff() {
            return initialBackoff == null ? Duration.ofMillis(500) : initialBackoff;
        }
    }

    /** retry 缺省时返回空策略（全部走默认值）。 */
    public RetryPolicy retryOrDefault() {
        return retry == null ? new RetryPolicy(null, null) : retry;
    }
}
