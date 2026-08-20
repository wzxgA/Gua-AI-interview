package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.AiTierConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** AI 档位配置 Mapper。 */
public interface AiTierConfigMapper extends BaseMapper<AiTierConfigEntity> {

    /** UPSERT：按 tier 主键，非 NULL 字段覆盖已有行（NULL 不覆盖，用于部分更新）。 */
    @Insert(
            "<script>INSERT INTO ai_tier_config  (tier, provider, model, temperature, max_tokens,"
                + " dimensions, fallback,   thinking, reasoning_effort, override_base_url,"
                + " override_api_key_enc, updated_by, updated_at) VALUES  (#{tier}, #{provider},"
                + " #{model}, #{temperature}, #{maxTokens}, #{dimensions}, #{fallback},  "
                + " #{thinking}, #{reasoningEffort}, #{overrideBaseUrl}, #{overrideApiKeyEnc},"
                + " #{updatedBy}, now()) ON CONFLICT (tier) DO UPDATE SET  provider ="
                + " COALESCE(EXCLUDED.provider, ai_tier_config.provider),  model ="
                + " COALESCE(EXCLUDED.model, ai_tier_config.model),  temperature ="
                + " COALESCE(EXCLUDED.temperature, ai_tier_config.temperature),  max_tokens ="
                + " COALESCE(EXCLUDED.max_tokens, ai_tier_config.max_tokens),  dimensions ="
                + " COALESCE(EXCLUDED.dimensions, ai_tier_config.dimensions),  fallback ="
                + " COALESCE(EXCLUDED.fallback, ai_tier_config.fallback),  thinking ="
                + " COALESCE(EXCLUDED.thinking, ai_tier_config.thinking),  reasoning_effort ="
                + " COALESCE(EXCLUDED.reasoning_effort, ai_tier_config.reasoning_effort), "
                + " override_base_url = COALESCE(EXCLUDED.override_base_url,"
                + " ai_tier_config.override_base_url),  override_api_key_enc ="
                + " COALESCE(EXCLUDED.override_api_key_enc, ai_tier_config.override_api_key_enc), "
                + " updated_by = COALESCE(EXCLUDED.updated_by, ai_tier_config.updated_by), "
                + " updated_at = now()</script>")
    int upsert(AiTierConfigEntity entity);

    /** 清空某 tier 的 override apiKey 覆盖（回退 provider/yml）。 */
    @Insert(
            "<script>"
                    + "UPDATE ai_tier_config SET override_api_key_enc = NULL, updated_at = now()"
                    + " WHERE tier = #{tier}"
                    + "</script>")
    int clearOverrideApiKey(@Param("tier") String tier);

    /** 清空某 tier 的 override baseUrl 覆盖（回退 provider/yml）。 */
    @Insert(
            "<script>"
                    + "UPDATE ai_tier_config SET override_base_url = NULL, updated_at = now()"
                    + " WHERE tier = #{tier}"
                    + "</script>")
    int clearOverrideBaseUrl(@Param("tier") String tier);
}
