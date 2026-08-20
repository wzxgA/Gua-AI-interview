package com.aims.gateway.controller.system;

/**
 * 保存 TTS 连接配置的请求体（PUT /api/v1/system/tts-config）。
 *
 * <p>字段覆盖语义：
 *
 * <ul>
 *   <li>{@code null}（不传）→ 保留 DB 旧值（未配置过则沿用 yml）
 *   <li>{@code ""}（空串，仅字符串字段）→ 清除 DB 覆盖，回退 yml
 *   <li>非空 → 覆盖
 * </ul>
 *
 * <p>{@link #apiKey()} 特殊：非空 = 加密后覆盖；空串 = 清除 DB 覆盖回退 yml。
 *
 * @param enabled 全局启用开关（三态）
 * @param provider 引擎标识（默认 volc）
 * @param baseUrl 接口地址
 * @param apiKey API Key
 * @param resourceId 火山资源 ID
 * @param defaultSpeaker 默认音色
 * @param format 音频格式（mp3/wav/pcm）
 * @param sampleRate 采样率（16000/24000/48000）
 * @param speechRate 语速
 * @param personaVoiceLink 人设联动音色
 */
public record SaveTtsConfigCommand(
        Boolean enabled,
        String provider,
        String baseUrl,
        String apiKey,
        String resourceId,
        String defaultSpeaker,
        String format,
        Integer sampleRate,
        Integer speechRate,
        Boolean personaVoiceLink) {}
