package com.aims.infra.service.impl;

import com.aims.core.interview.InterviewerPersona;
import com.aims.infra.config.TtsProperties;
import com.aims.infra.service.TtsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 火山引擎豆包 TTS 2.0 实现。
 *
 * <p>使用 HTTP POST 接口（AgentPlan 路径），一次性传入完整文本，流式接收 base64 音频帧拼接为完整 MP3，上传 MinIO。失败时静默降级返回 null。
 */
@Service
@ConditionalOnProperty(prefix = "aims.tts", name = "enabled", havingValue = "true")
public class VolcanoTtsService implements TtsService {

    private static final Logger log = LoggerFactory.getLogger(VolcanoTtsService.class);
    private static final String BUCKET = "aims-audio";

    private final TtsProperties ttsProperties;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public VolcanoTtsService(TtsProperties ttsProperties, MinioClient minioClient) {
        this.ttsProperties = ttsProperties;
        this.minioClient = minioClient;
    }

    @Override
    public TtsResult synthesize(String text, InterviewerPersona persona) {
        try {
            // 1. 构造请求体
            String speaker = resolveSpeaker(persona);
            String requestBody = buildRequestBody(text, speaker);

            // 2. 调用火山引擎 TTS API
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(ttsProperties.baseUrl()))
                            .header("X-Api-Key", ttsProperties.apiKey())
                            .header("X-Api-Resource-Id", ttsProperties.resourceId())
                            .header("X-Api-Request-Id", UUID.randomUUID().toString())
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

            // 3. 一次性读取全部响应体（避免 Stream 只能消费一次的问题）
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn(
                        "TTS API 返回非 200 statusCode={} body={}",
                        response.statusCode(),
                        truncate(response.body(), 500));
                return null;
            }

            // 4. 逐行解析 JSON，收集音频帧 + 提取时长
            var decoder = Base64.getDecoder();
            var buffer = new ByteArrayOutputStream();
            int durationMs = 0;

            for (String line : response.body().split("\n")) {
                if (line.isBlank()) continue;
                JsonNode node = objectMapper.readTree(line);
                String event = node.path("event").asText();
                if ("TTSResponse".equals(event)) {
                    buffer.write(decoder.decode(node.path("data").asText()));
                } else if ("TTSStopped".equals(event)) {
                    durationMs = node.path("duration_ms").asInt(0);
                }
            }

            byte[] audioBytes = buffer.toByteArray();
            if (audioBytes.length == 0) {
                log.warn("TTS 合成音频为空 textLen={}", text.length());
                return null;
            }

            // 5. 上传 MinIO
            String objectName = "tts/" + UUID.randomUUID() + ".mp3";
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(BUCKET).object(objectName).stream(
                                    new ByteArrayInputStream(audioBytes), audioBytes.length, -1)
                            .contentType("audio/mpeg")
                            .build());

            String audioUrl = "/" + BUCKET + "/" + objectName;
            log.info(
                    "TTS 合成成功 audioUrl={} durationMs={} textLen={}",
                    audioUrl,
                    durationMs,
                    text.length());
            return new TtsResult(audioUrl, durationMs);

        } catch (Exception e) {
            log.error("TTS 合成失败，静默降级 textLen={}", text.length(), e);
            return null;
        }
    }

    /** 根据人设解析音色。 */
    private String resolveSpeaker(InterviewerPersona persona) {
        if (!ttsProperties.personaVoiceLink()) {
            return ttsProperties.defaultSpeaker();
        }
        return switch (persona) {
            case FRIENDLY -> "zh_female_vv_uranus_bigtts";
            case PRESSURE -> "zh_male_xiaotian_mars_bigtts";
            case TECHNICAL -> "zh_male_yunzhou_moon_bigtts";
        };
    }

    /** 构造请求体 JSON。 */
    private String buildRequestBody(String text, String speaker) throws Exception {
        Map<String, Object> body =
                Map.of(
                        "user", Map.of("uid", "aims-interview"),
                        "namespace", "BidirectionalTTS",
                        "req_params",
                                Map.of(
                                        "text",
                                        text,
                                        "speaker",
                                        speaker,
                                        "audio_params",
                                        Map.of(
                                                "format", ttsProperties.format(),
                                                "sample_rate", ttsProperties.sampleRate(),
                                                "speech_rate", ttsProperties.speechRate()),
                                        "additions",
                                        Map.of("disable_markdown_filter", true)));
        return objectMapper.writeValueAsString(body);
    }

    /** 截断字符串用于日志输出。 */
    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
