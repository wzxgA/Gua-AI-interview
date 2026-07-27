package com.aims.ai.router;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.AiException;
import com.aims.core.common.exception.AiOutputParseException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * 多模型路由器：按档位解析模型句柄，并负责"换模型兜底"的降级。
 *
 * <p>职责边界：Advisor（RetryAdvisor）负责"原地重试"；Router 负责主模型失败后的 fallback 切换，二者不重复触发。 降级事件输出 WARN 日志 +
 * Micrometer 计数器 {@code aims.model.fallback{tier,from,to}}。
 */
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final Map<ModelTier, ModelHandle> handles;
    private final ModelTier defaultTier;
    private final MeterRegistry meterRegistry;

    public ModelRouter(
            Map<ModelTier, ModelHandle> handles,
            ModelTier defaultTier,
            MeterRegistry meterRegistry) {
        this.handles = Map.copyOf(handles);
        this.defaultTier = defaultTier;
        this.meterRegistry = meterRegistry;
    }

    /** 按档位解析句柄（O(1) 纯内存）；档位未配置时抛出业务异常。 */
    public ModelHandle resolve(ModelTier tier) {
        ModelHandle handle = handles.get(tier);
        if (handle == null) {
            throw new AiException(ErrorCode.MODEL_TIER_UNSUPPORTED, "未配置的模型档位: " + tier);
        }
        return handle;
    }

    /** 默认档位（配置 aims.ai.default-tier）。 */
    public ModelHandle resolveDefault() {
        return resolve(defaultTier);
    }

    /**
     * 阻塞调用 + 降级：主模型调用失败 → 换 fallback 重试 1 次 → 仍失败抛出业务异常。
     *
     * <p>{@link AiOutputParseException}（结构化输出解析失败）不触发降级，直接抛出。
     *
     * @param action 入参为当前生效的 ChatClient 与模型名（fallback 时为降级模型名，保证计量归因正确）
     */
    public <T> T executeCall(ModelTier tier, BiFunction<ChatClient, String, T> action) {
        ModelHandle handle = resolve(tier);
        try {
            return action.apply(handle.chatClient(), handle.config().model());
        } catch (AiOutputParseException e) {
            throw e;
        } catch (Exception primary) {
            if (!handle.hasFallback()) {
                throw new AiException(
                        ErrorCode.MODEL_CALL_FAILED,
                        "模型调用失败 tier=" + tier + " model=" + handle.config().model(),
                        primary);
            }
            recordFallback(tier, handle.config().model(), handle.fallbackModel(), primary);
            try {
                return action.apply(handle.fallbackClient(), handle.fallbackModel());
            } catch (Exception fallback) {
                fallback.addSuppressed(primary);
                throw new AiException(
                        ErrorCode.MODEL_CALL_FAILED,
                        "主备模型均调用失败 tier=" + tier + " fallback=" + handle.fallbackModel(),
                        fallback);
            }
        }
    }

    /**
     * 流式调用 + 降级：主模型流失败时切换到 fallback 重新发起（注意：若失败发生在流中段， 降级流将从头重放，消费方需感知；P3 接入 WebSocket 时再细化断点策略）。
     */
    public Flux<String> executeStream(
            ModelTier tier, BiFunction<ChatClient, String, Flux<String>> action) {
        ModelHandle handle = resolve(tier);
        Flux<String> primary =
                Flux.defer(() -> action.apply(handle.chatClient(), handle.config().model()));
        if (!handle.hasFallback()) {
            return primary;
        }
        return primary.onErrorResume(
                ex -> {
                    recordFallback(tier, handle.config().model(), handle.fallbackModel(), ex);
                    return Flux.defer(
                            () -> action.apply(handle.fallbackClient(), handle.fallbackModel()));
                });
    }

    private void recordFallback(ModelTier tier, String from, String to, Throwable cause) {
        log.warn("模型降级 tier={} from={} to={} cause={}", tier, from, to, cause.toString());
        meterRegistry
                .counter("aims.model.fallback", "tier", tier.name(), "from", from, "to", to)
                .increment();
    }
}
