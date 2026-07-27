package com.aims.ai.facade;

import com.aims.ai.advisor.AiAdvisorContext;
import com.aims.ai.router.ModelRouter;
import com.aims.ai.router.ModelTier;
import com.aims.core.common.exception.AiOutputParseException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

/** {@link AiChatFacade} 默认实现：经由 {@link ModelRouter} 路由并统一写入 Advisor 归因参数。 */
@Component
public class DefaultAiChatFacade implements AiChatFacade {

    private final ModelRouter modelRouter;

    public DefaultAiChatFacade(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    @Override
    public <T> T callForEntity(
            ModelTier tier, String systemPrompt, String userPrompt, Class<T> type) {
        return modelRouter.executeCall(
                tier,
                (client, model) -> {
                    try {
                        return newSpec(client, tier, model, systemPrompt, userPrompt)
                                .call()
                                .entity(type);
                    } catch (Exception first) {
                        if (isModelCallFailure(first)) {
                            // 模型调用异常：上抛给 Router 走降级，不做本地重试（RetryAdvisor 已原地重试过）
                            throw first;
                        }
                        // 疑似输出解析失败：重试 1 次，仍失败则抛 AiOutputParseException
                        try {
                            return newSpec(client, tier, model, systemPrompt, userPrompt)
                                    .call()
                                    .entity(type);
                        } catch (Exception second) {
                            if (isModelCallFailure(second)) {
                                throw second;
                            }
                            throw new AiOutputParseException(
                                    "结构化输出解析失败 type=" + type.getSimpleName() + " model=" + model,
                                    second);
                        }
                    }
                });
    }

    @Override
    public String call(ModelTier tier, String systemPrompt, String userPrompt) {
        return modelRouter.executeCall(
                tier,
                (client, model) ->
                        newSpec(client, tier, model, systemPrompt, userPrompt).call().content());
    }

    @Override
    public Flux<String> stream(ModelTier tier, String systemPrompt, String userPrompt) {
        return modelRouter.executeStream(
                tier,
                (client, model) ->
                        newSpec(client, tier, model, systemPrompt, userPrompt).stream().content());
    }

    @Override
    public Flux<String> streamWithMemory(
            ModelTier tier, String conversationId, String systemPrompt, String userPrompt) {
        throw new UnsupportedOperationException("P3 交付：接入 ChatMemory 后开放");
    }

    /** 构造请求并写入 Advisor 归因参数（tier/model），降级时 model 自动切换为 fallback 模型。 */
    private ChatClient.ChatClientRequestSpec newSpec(
            ChatClient client,
            ModelTier tier,
            String model,
            String systemPrompt,
            String userPrompt) {
        return client.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(
                        advisor ->
                                advisor.param(AiAdvisorContext.TIER, tier.name())
                                        .param(AiAdvisorContext.MODEL, model));
    }

    /** 判定异常是否为模型调用失败（网络/HTTP/限流等），而非输出解析失败。 */
    private boolean isModelCallFailure(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof TransientAiException
                    || current instanceof NonTransientAiException
                    || current instanceof RestClientResponseException
                    || current instanceof ResourceAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
