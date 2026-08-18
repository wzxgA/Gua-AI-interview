package com.aims.ai.facade;

import com.aims.ai.router.ModelTier;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * AI 对话统一入口。业务代码（P3 起的 Agent）不直接感知 {@link ModelTier} 映射细节与 Spring AI API， 统一入口便于：Advisor
 * 统一挂载、计量归因、未来降级为纯阻塞调用或替换框架。
 */
public interface AiChatFacade {

    /** 阻塞调用 + 结构化输出（P3 面试计划、P4 评估打分使用）。 */
    <T> T callForEntity(ModelTier tier, String systemPrompt, String userPrompt, Class<T> type);

    /** 阻塞纯文本调用。 */
    String call(ModelTier tier, String systemPrompt, String userPrompt);

    /** 带工具注册的阻塞调用（F2）：模型可自主调用 {@code tools}（@Tool 实例），工具结果自动回填上下文后产出最终文本。 */
    String callWithTools(
            ModelTier tier, String systemPrompt, String userPrompt, List<Object> tools);

    /** 流式调用（P3 WebSocket 流式提问使用）。 */
    Flux<String> stream(ModelTier tier, String systemPrompt, String userPrompt);

    /** 带上下文的调用（P3 接入 ChatMemory 时使用，P1 仅定契约）。 */
    Flux<String> streamWithMemory(
            ModelTier tier, String conversationId, String systemPrompt, String userPrompt);
}
