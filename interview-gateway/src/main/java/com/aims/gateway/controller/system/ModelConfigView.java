package com.aims.gateway.controller.system;

import java.util.List;

/**
 * 生效的 AI 模型配置（GET /api/v1/system/model-config 响应）。
 *
 * <p>返回 yml 与 DB 合并后的生效值；API Key 一律掩码回显，明文永不出现。source 标记该字段来源 （db = DB 有覆盖，yml = 沿用配置文件/环境变量）。
 *
 * @param defaultTier 默认档位名
 * @param providers provider 级配置（含掩码 key 与来源）
 * @param tiers 档位级配置（含 override url/掩码 key 与来源）
 */
public record ModelConfigView(
        String defaultTier, List<ProviderView> providers, List<TierView> tiers) {

    public record ProviderView(
            String name,
            String baseUrl,
            String apiKeyMasked,
            String source,
            Integer maxConcurrency,
            /** true = yml 内置 provider（不可改名/删除）；false = DB 自定义 provider。 */
            boolean builtin) {}

    public record TierView(
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
            String overrideApiKeyMasked,
            String source) {}
}
