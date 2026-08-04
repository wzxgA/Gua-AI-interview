package com.aims.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** TTS 语音合成配置（前缀 {@code aims.tts}）。 */
@ConfigurationProperties(prefix = "aims.tts")
public record TtsProperties(
        boolean enabled,
        String provider,
        String apiKey,
        String resourceId,
        String baseUrl,
        String defaultSpeaker,
        String format,
        int sampleRate,
        int speechRate,
        boolean personaVoiceLink) {

    public TtsProperties {
        if (provider == null || provider.isBlank()) {
            provider = "volc";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openspeech.bytedance.com/api/v3/plan/tts/unidirectional";
        }
        if (resourceId == null || resourceId.isBlank()) {
            resourceId = "seed-tts-2.0";
        }
        if (defaultSpeaker == null || defaultSpeaker.isBlank()) {
            defaultSpeaker = "zh_male_m191_uranus_bigtts";
        }
        if (format == null || format.isBlank()) {
            format = "mp3";
        }
        if (sampleRate == 0) {
            sampleRate = 24000;
        }
    }
}
