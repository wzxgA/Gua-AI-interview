package com.aims.gateway.controller;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.common.Result;
import com.aims.infra.config.InfraHealthIndicator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** P1 冒烟接口：验证 AI 管线与基础设施连通性。仅 local/dev 环境加载，生产 profile 不注册。 */
@Profile({"local", "dev"})
@Validated
@RestController
@RequestMapping("/api/smoke")
@Tag(name = "smoke", description = "P1 冒烟接口（仅 local/dev 环境加载）")
public class SmokeController {

    private static final String SMOKE_SYSTEM_PROMPT = "你是面试平台的冒烟测试助手，回答保持简洁。";

    private final AiChatFacade chatFacade;
    private final InfraHealthIndicator infraHealthIndicator;

    public SmokeController(AiChatFacade chatFacade, InfraHealthIndicator infraHealthIndicator) {
        this.chatFacade = chatFacade;
        this.infraHealthIndicator = infraHealthIndicator;
    }

    @Operation(summary = "阻塞文本调用", description = "验证指定档位的模型阻塞调用链路")
    @GetMapping("/chat")
    public Result<String> chat(
            @RequestParam @NotBlank(message = "prompt 不能为空") String prompt,
            @RequestParam(defaultValue = "STANDARD") ModelTier tier) {
        return Result.ok(chatFacade.call(tier, SMOKE_SYSTEM_PROMPT, prompt));
    }

    @Operation(summary = "SSE 流式调用", description = "验证指定档位的流式输出链路")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam @NotBlank(message = "prompt 不能为空") String prompt,
            @RequestParam(defaultValue = "FLAGSHIP") ModelTier tier) {
        return chatFacade.stream(tier, SMOKE_SYSTEM_PROMPT, prompt);
    }

    @Operation(summary = "结构化输出", description = "验证 entity(type) 结构化输出链路，返回内置演示对象")
    @GetMapping("/entity")
    public Result<SmokeSummary> entity() {
        SmokeSummary summary =
                chatFacade.callForEntity(
                        ModelTier.STANDARD,
                        "你是结构化数据生成器，严格按要求的 JSON Schema 输出。",
                        "生成一份关于“Java 后端面试”的会话摘要：topic 用一句话概括，keyPoints 给出 3 个要点。",
                        SmokeSummary.class);
        return Result.ok(summary);
    }

    @Operation(summary = "基础设施连通性", description = "聚合 PG(vector 扩展)/Redis/Kafka/MinIO 连通状态")
    @GetMapping("/infra")
    public Result<Map<String, Object>> infra() {
        Health health = infraHealthIndicator.health();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", health.getStatus().getCode());
        health.getDetails().forEach(body::put);
        return Result.ok(body);
    }

    /** 结构化输出演示模型。 */
    public record SmokeSummary(String topic, List<String> keyPoints) {}
}
