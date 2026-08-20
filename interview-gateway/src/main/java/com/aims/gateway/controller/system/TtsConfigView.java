package com.aims.gateway.controller.system;

import java.time.Instant;

/**
 * 生效的 TTS 连接配置（GET /api/v1/system/tts-config 响应）。
 *
 * <p>返回 yml 与 DB 合并后的生效值；API Key 一律掩码回显，明文永不出现。每字段附 {@code source}（db = DB 有覆盖，yml = 沿用配置文件/环境变量）。
 *
 * @param enabled 生效的全局启用开关
 * @param enabledSource
 * @param provider 引擎标识
 * @param providerSource
 * @param baseUrl 接口地址
 * @param baseUrlSource
 * @param apiKeyMasked 掩码后的 API Key（空表示未配置）
 * @param apiKeySource
 * @param resourceId 火山资源 ID
 * @param resourceIdSource
 * @param defaultSpeaker 默认音色
 * @param defaultSpeakerSource
 * @param format 音频格式
 * @param formatSource
 * @param sampleRate 采样率
 * @param sampleRateSource
 * @param speechRate 语速
 * @param speechRateSource
 * @param personaVoiceLink 人设联动音色
 * @param personaVoiceLinkSource
 * @param updatedBy 最近操作人
 * @param updatedAt 最近更新时间
 */
public record TtsConfigView(
        Boolean enabled,
        String enabledSource,
        String provider,
        String providerSource,
        String baseUrl,
        String baseUrlSource,
        String apiKeyMasked,
        String apiKeySource,
        String resourceId,
        String resourceIdSource,
        String defaultSpeaker,
        String defaultSpeakerSource,
        String format,
        String formatSource,
        Integer sampleRate,
        String sampleRateSource,
        Integer speechRate,
        String speechRateSource,
        Boolean personaVoiceLink,
        String personaVoiceLinkSource,
        String updatedBy,
        Instant updatedAt) {}
