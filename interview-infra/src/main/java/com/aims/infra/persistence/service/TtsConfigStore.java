package com.aims.infra.persistence.service;

import com.aims.infra.persistence.entity.TtsConfigEntity;

/**
 * TTS 连接配置持久化（DB 增量覆盖层，单行 id=1）。
 *
 * <p>仅提供纯 DB 读写；API Key 的加密/解密与 yml 合并由调用方（TtsConfigResolver / gateway 的
 * TtsConfigService）负责，本接口不感知密钥。
 */
public interface TtsConfigStore {

    /** 查询当前 DB 覆盖配置；未配置过返回 null（沿用 yml）。 */
    TtsConfigEntity current();

    /** UPSERT 单行配置（NULL 字段不覆盖已有值）。 */
    void upsert(TtsConfigEntity entity);

    /** 清空某字段的 DB 覆盖（回退 yml）。 */
    void clearBaseUrl();

    void clearApiKey();

    void clearResourceId();

    void clearDefaultSpeaker();

    /** 删除整行，全部回退 yml。 */
    void resetAll();
}
