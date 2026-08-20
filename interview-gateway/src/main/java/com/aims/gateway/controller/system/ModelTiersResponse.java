package com.aims.gateway.controller.system;

import com.aims.ai.config.AiModelProperties;
import com.aims.ai.router.ModelTier;
import java.util.List;

/** 模型档位配置响应（设置页展示用，不暴露 API Key 等敏感信息）。 */
public record ModelTiersResponse(String defaultTier, List<TierItem> tiers) {

    public static ModelTiersResponse from(AiModelProperties props) {
        List<TierItem> items =
                props.tiers().entrySet().stream()
                        .sorted(
                                (a, b) ->
                                        Integer.compare(a.getKey().ordinal(), b.getKey().ordinal()))
                        .map(e -> TierItem.from(e.getKey(), e.getValue()))
                        .toList();
        return new ModelTiersResponse(props.defaultTier().name(), items);
    }

    /** 单个档位配置。 */
    public record TierItem(
            String tier,
            String provider,
            String model,
            Double temperature,
            Integer maxTokens,
            Integer dimensions,
            String fallback) {

        static TierItem from(ModelTier tier, AiModelProperties.TierConfig cfg) {
            return new TierItem(
                    tier.name(),
                    // 档位 override 时 provider 为 "<TIER>@override" 内部名，展示还原为原始 provider
                    cfg.provider().replaceAll("@override$", ""),
                    cfg.model(),
                    cfg.temperature(),
                    cfg.maxTokens(),
                    cfg.dimensions(),
                    cfg.fallback());
        }
    }
}
