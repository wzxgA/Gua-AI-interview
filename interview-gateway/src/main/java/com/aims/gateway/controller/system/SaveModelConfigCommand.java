package com.aims.gateway.controller.system;

import java.util.List;

/**
 * 保存 AI 模型配置的请求体（PUT /api/v1/system/model-config）。
 *
 * <p>apiKey 语义：
 *
 * <ul>
 *   <li>{@code null}（不传）→ 保留 DB 旧值（未配置过则沿用 yml）
 *   <li>{@code ""}（空串）→ 清除 DB 覆盖，回退 yml
 *   <li>非空 → 加密后覆盖
 * </ul>
 *
 * <p>tier 的 overrideBaseUrl / overrideApiKey 语义相同：非空覆盖，空串清空，null 保留。
 *
 * @param providers provider 级配置（baseUrl 总是提交）
 * @param tiers 档位级配置（仅提交需要覆盖的档位与字段）
 */
public record SaveModelConfigCommand(List<ProviderItem> providers, List<TierItem> tiers) {

    public record ProviderItem(
            String name, String baseUrl, String apiKey, Integer maxConcurrency) {}

    public record TierItem(
            String tier,
            String provider,
            String model,
            Double temperature,
            Integer maxTokens,
            Integer dimensions,
            String fallback,
            Boolean thinking,
            String reasoningEffort,
            String overrideBaseUrl,
            String overrideApiKey) {}
}
