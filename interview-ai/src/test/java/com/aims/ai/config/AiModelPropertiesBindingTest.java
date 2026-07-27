package com.aims.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aims.ai.router.ModelTier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** AiModelProperties 配置绑定切片测试。 */
class AiModelPropertiesBindingTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(EnableConfig.class)
                    .withPropertyValues(
                            "aims.ai.default-tier=STANDARD",
                            "aims.ai.providers.dashscope.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1",
                            "aims.ai.providers.dashscope.api-key=sk-test-dashscope",
                            "aims.ai.providers.deepseek.base-url=https://api.deepseek.com/v1",
                            "aims.ai.providers.deepseek.api-key=sk-test-deepseek",
                            "aims.ai.tiers.FLAGSHIP.provider=dashscope",
                            "aims.ai.tiers.FLAGSHIP.model=qwen-max",
                            "aims.ai.tiers.FLAGSHIP.temperature=0.7",
                            "aims.ai.tiers.FLAGSHIP.max-tokens=2048",
                            "aims.ai.tiers.FLAGSHIP.fallback=deepseek:deepseek-chat",
                            "aims.ai.tiers.STANDARD.provider=deepseek",
                            "aims.ai.tiers.STANDARD.model=deepseek-chat",
                            "aims.ai.pricing.qwen-max.input=0.04",
                            "aims.ai.pricing.qwen-max.output=0.12");

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiModelProperties.class)
    static class EnableConfig {}

    @Test
    void bindsProvidersTiersAndPricing() {
        runner.run(
                context -> {
                    AiModelProperties props = context.getBean(AiModelProperties.class);

                    assertThat(props.defaultTier()).isEqualTo(ModelTier.STANDARD);
                    assertThat(props.providers()).containsOnlyKeys("dashscope", "deepseek");

                    AiModelProperties.TierConfig flagship = props.tiers().get(ModelTier.FLAGSHIP);
                    assertThat(flagship.provider()).isEqualTo("dashscope");
                    assertThat(flagship.model()).isEqualTo("qwen-max");
                    assertThat(flagship.fallback()).isEqualTo("deepseek:deepseek-chat");
                    assertThat(flagship.temperature()).isEqualTo(0.7);

                    assertThat(props.pricing().get("qwen-max").input())
                            .isEqualByComparingTo("0.04");
                });
    }

    @Test
    void appliesRetryAndConcurrencyDefaults() {
        runner.run(
                context -> {
                    AiModelProperties props = context.getBean(AiModelProperties.class);

                    assertThat(props.retryOrDefault().effectiveMaxAttempts()).isEqualTo(2);
                    assertThat(props.retryOrDefault().effectiveInitialBackoff().toMillis())
                            .isEqualTo(500);
                    assertThat(props.providers().get("dashscope").effectiveMaxConcurrency())
                            .isEqualTo(32);
                    assertThat(props.providers().get("dashscope").effectiveApiKey())
                            .isEqualTo("sk-test-dashscope");
                });
    }

    @Test
    void failsWhenApiKeyMissing() {
        // api-key 置空违反 @NotBlank，上下文启动即失败（不允许静默用空 Key）
        runner.withPropertyValues("aims.ai.providers.dashscope.api-key=")
                .run(context -> assertThat(context).hasFailed());
    }
}
