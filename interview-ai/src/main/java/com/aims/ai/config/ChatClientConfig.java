package com.aims.ai.config;

import com.aims.ai.advisor.LoggingAdvisor;
import com.aims.ai.advisor.RetryAdvisor;
import com.aims.ai.advisor.TokenMeterAdvisor;
import com.aims.ai.router.ModelHandle;
import com.aims.ai.router.ModelRouter;
import com.aims.ai.router.ModelTier;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 装配：定义基础 Advisor 与句柄工厂，并按 yml 配置构建 {@link ModelRouter} 初始快照。
 *
 * <p>运行中可通过 {@link ModelRouter#refresh}（DB 覆盖配置热更新）原子替换句柄快照， 构建细节统一收敛在 {@link ModelHandleFactory}。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiModelProperties.class)
public class ChatClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatClientConfig.class);

    @Bean
    LoggingAdvisor loggingAdvisor(@Value("${aims.log.prompt-max-chars:200}") int promptMaxChars) {
        return new LoggingAdvisor(promptMaxChars);
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
            ModelHandleFactory modelHandleFactory,
            MeterRegistry meterRegistry) {
        Map<String, OpenAiApi> apis = modelHandleFactory.buildApis(properties);
        Map<ModelTier, ModelHandle> handles = modelHandleFactory.buildHandles(properties, apis);
        log.info("模型路由装配完成 tiers={} default={}", handles.keySet(), properties.defaultTier());
        return new ModelRouter(
                modelHandleFactory, handles, properties.defaultTier(), meterRegistry);
    }
}
