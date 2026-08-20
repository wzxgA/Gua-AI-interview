package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.TtsConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;

/**
 * TTS 配置 Mapper（单行，id=1）。
 *
 * <p>UPSERT 使用 COALESCE 实现"NULL 不覆盖已有值，非 NULL 覆盖"，用于部分更新；需要主动置 NULL 回退 yml 的字段单独提供 clear 方法。
 */
public interface TtsConfigMapper extends BaseMapper<TtsConfigEntity> {

    @Insert(
            "<script>INSERT INTO tts_config (id, enabled, provider, base_url, api_key_enc,"
                + " resource_id, default_speaker, format, sample_rate, speech_rate,"
                + " persona_voice_link, updated_by, updated_at) VALUES (1, #{enabled}, #{provider},"
                + " #{baseUrl}, #{apiKeyEnc}, #{resourceId}, #{defaultSpeaker}, #{format},"
                + " #{sampleRate}, #{speechRate}, #{personaVoiceLink}, #{updatedBy}, now()) ON"
                + " CONFLICT (id) DO UPDATE SET enabled = COALESCE(EXCLUDED.enabled,"
                + " tts_config.enabled), provider = COALESCE(EXCLUDED.provider,"
                + " tts_config.provider), base_url = COALESCE(EXCLUDED.base_url,"
                + " tts_config.base_url), api_key_enc = COALESCE(EXCLUDED.api_key_enc,"
                + " tts_config.api_key_enc), resource_id = COALESCE(EXCLUDED.resource_id,"
                + " tts_config.resource_id), default_speaker = COALESCE(EXCLUDED.default_speaker,"
                + " tts_config.default_speaker), format = COALESCE(EXCLUDED.format,"
                + " tts_config.format), sample_rate = COALESCE(EXCLUDED.sample_rate,"
                + " tts_config.sample_rate), speech_rate = COALESCE(EXCLUDED.speech_rate,"
                + " tts_config.speech_rate), persona_voice_link ="
                + " COALESCE(EXCLUDED.persona_voice_link, tts_config.persona_voice_link),"
                + " updated_by = COALESCE(EXCLUDED.updated_by, tts_config.updated_by), updated_at ="
                + " now()</script>")
    int upsert(TtsConfigEntity entity);

    @Insert("UPDATE tts_config SET base_url = NULL, updated_at = now() WHERE id = 1")
    int clearBaseUrl();

    /** 清空 apiKey 覆盖（回退 yml）。 */
    @Insert("UPDATE tts_config SET api_key_enc = NULL, updated_at = now() WHERE id = 1")
    int clearApiKey();

    @Insert("UPDATE tts_config SET resource_id = NULL, updated_at = now() WHERE id = 1")
    int clearResourceId();

    @Insert("UPDATE tts_config SET default_speaker = NULL, updated_at = now() WHERE id = 1")
    int clearDefaultSpeaker();

    @Insert("DELETE FROM tts_config WHERE id = 1")
    int deleteRow();
}
