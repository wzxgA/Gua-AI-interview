package com.aims.infra.persistence.service;

import com.aims.infra.persistence.entity.AiProviderConfigEntity;
import com.aims.infra.persistence.entity.AiTierConfigEntity;
import java.util.List;

/**
 * AI 模型配置持久化（DB 增量覆盖层）。
 *
 * <p>仅提供纯 DB 读写；API Key 的加密/解密与 yml 合并由调用方（gateway 的 ModelConfigService）负责， 本接口不感知密钥，也不感知
 * AiModelProperties。
 */
public interface AiModelConfigStore {

    /** 查询全部 provider 覆盖配置（空表返回空列表）。 */
    List<AiProviderConfigEntity> listProviders();

    /** 查询全部 tier 覆盖配置（空表返回空列表）。 */
    List<AiTierConfigEntity> listTiers();

    /** UPSERT 一条 provider 配置（NULL 字段不覆盖已有值）。 */
    void upsertProvider(AiProviderConfigEntity entity);

    /** 清空某 provider 的 apiKey 覆盖（回退 yml）。 */
    void clearProviderApiKey(String name);

    /** UPSERT 一条 tier 配置（NULL 字段不覆盖已有值）。 */
    void upsertTier(AiTierConfigEntity entity);

    /** 删除单个 provider 配置（自定义 provider 移除）。 */
    void deleteProvider(String name);

    /** 清空某 tier 的 overrideBaseUrl 覆盖（回退 provider/yml）。 */
    void clearTierOverrideBaseUrl(String tier);

    /** 清空某 tier 的 overrideApiKey 覆盖（回退 provider/yml）。 */
    void clearTierOverrideApiKey(String tier);

    /** 清空全部 DB 覆盖配置（恢复默认，回退 yml）。 */
    void resetAll();
}
