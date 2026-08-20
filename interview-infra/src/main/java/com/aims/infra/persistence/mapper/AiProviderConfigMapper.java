package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.AiProviderConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** AI provider 配置 Mapper。 */
public interface AiProviderConfigMapper extends BaseMapper<AiProviderConfigEntity> {

    /** UPSERT：按 name 主键，非 NULL 字段覆盖已有行（NULL 不覆盖，用于部分更新）。 */
    @Insert(
            "<script>INSERT INTO ai_provider_config  (name, base_url, api_key_enc, max_concurrency,"
                + " updated_by, updated_at) VALUES  (#{name}, #{baseUrl}, #{apiKeyEnc},"
                + " #{maxConcurrency}, #{updatedBy}, now()) ON CONFLICT (name) DO UPDATE SET "
                + " base_url = COALESCE(EXCLUDED.base_url, ai_provider_config.base_url), "
                + " api_key_enc = COALESCE(EXCLUDED.api_key_enc, ai_provider_config.api_key_enc), "
                + " max_concurrency = COALESCE(EXCLUDED.max_concurrency,"
                + " ai_provider_config.max_concurrency),  updated_by ="
                + " COALESCE(EXCLUDED.updated_by, ai_provider_config.updated_by),  updated_at ="
                + " now()</script>")
    int upsert(AiProviderConfigEntity entity);

    /**
     * 清空某 provider 的 apiKey 覆盖（回退 yml）。
     *
     * <p>因为 {@link #upsert} 使用 COALESCE 无法主动置 NULL，需要独立 SQL。
     */
    @Insert(
            "<script>"
                    + "UPDATE ai_provider_config SET api_key_enc = NULL, updated_at = now()"
                    + " WHERE name = #{name}"
                    + "</script>")
    int clearApiKey(@Param("name") String name);
}
