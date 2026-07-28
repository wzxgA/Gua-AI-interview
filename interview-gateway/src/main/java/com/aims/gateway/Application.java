package com.aims.gateway;

import com.aims.core.common.TraceContext;
import org.slf4j.MDC;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AIMS 启动应用（仅 gateway 模块允许存在启动类与 Web 层）。
 *
 * <p>排除 OpenAI Audio/Image/Moderation 自动装配类：spring-ai-starter-model-openai 传递引入了 这些自动装配，需要
 * spring.ai.openai.api-key，但本项目由 ChatClientConfig 按档位手动装配， 不使用全局 OpenAI 配置，因此排除。
 */
@SpringBootApplication(
        scanBasePackages = "com.aims",
        exclude = {
            OpenAiAudioSpeechAutoConfiguration.class,
            OpenAiAudioTranscriptionAutoConfiguration.class,
            OpenAiImageAutoConfiguration.class,
            OpenAiModerationAutoConfiguration.class
        })
public class Application {

    public static void main(String[] args) {
        // 为 core 安装 traceId 提供方；P7 接入 OpenTelemetry 后替换为 OTel 实现
        TraceContext.register(() -> MDC.get("traceId"));
        SpringApplication.run(Application.class, args);
    }
}
