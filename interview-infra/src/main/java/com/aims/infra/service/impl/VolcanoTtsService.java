package com.aims.infra.service.impl;

import com.aims.core.interview.InterviewerPersona;
import com.aims.infra.config.TtsConfigResolver;
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
import java.util.HashMap;
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

    private final TtsConfigResolver ttsConfigResolver;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public VolcanoTtsService(TtsConfigResolver ttsConfigResolver, MinioClient minioClient) {
        this.ttsConfigResolver = ttsConfigResolver;
        this.minioClient = minioClient;
    }

    @Override
    public TtsResult synthesize(String text, InterviewerPersona persona) {
        try {
            // 0. 运行时解析生效配置（DB 覆盖 yml，下一题立即生效）
            TtsConfigResolver.ResolvedTts config = ttsConfigResolver.resolve();

            // 1. 构造请求体
            String speaker = resolveSpeaker(config, persona);
            String requestBody = buildRequestBody(config, text, speaker);

            // 2. 调用火山引擎 TTS API
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(config.baseUrl()))
                            .header("X-Api-Key", config.apiKey())
                            .header("X-Api-Resource-Id", config.resourceId())
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

            // 4. 解析响应：HTTP 流式接口返回 NDJSON（每行一个 JSON，各含一个 base64 音频分片）
            String body = response.body();
            log.debug("TTS 响应体长度={} 包含换行={}", body.length(), body.contains("\n"));

            var buffer = new ByteArrayOutputStream();
            int durationMs = 0;
            int lineCount = 0;

            for (String line : body.split("\n")) {
                if (line.isBlank()) continue;
                JsonNode node = objectMapper.readTree(line);
                lineCount++;

                int respCode = node.path("code").asInt(-1);
                if (respCode != 0 && respCode != 20000000) {
                    log.warn(
                            "TTS API 业务错误 code={} message={}",
                            respCode,
                            node.path("message").asText());
                    return null;
                }

                String chunk = node.path("data").asText();
                if (!chunk.isBlank()) {
                    buffer.write(Base64.getDecoder().decode(chunk));
                }
                if (node.has("duration_ms")) {
                    durationMs = node.path("duration_ms").asInt(0);
                }
            }

            log.debug("TTS 解析完成 共{}行 音频{}字节 durationMs={}", lineCount, buffer.size(), durationMs);

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
    private String resolveSpeaker(
            TtsConfigResolver.ResolvedTts config, InterviewerPersona persona) {
        if (!config.personaVoiceLink()) {
            return config.defaultSpeaker();
        }
        return switch (persona) {
            case FRIENDLY -> "zh_female_vv_uranus_bigtts";
            case PRESSURE -> "zh_male_taocheng_uranus_bigtts";
            case TECHNICAL -> "zh_male_m191_uranus_bigtts";
        };
    }

    /** 构造请求体 JSON。 */
    @SuppressWarnings("unchecked")
    private String buildRequestBody(
            TtsConfigResolver.ResolvedTts config, String text, String speaker) throws Exception {
        Map<String, Object> audioParams = new HashMap<>();
        audioParams.put("format", config.format());
        audioParams.put("sample_rate", config.sampleRate());
        audioParams.put("speech_rate", config.speechRate());

        Map<String, Object> reqParams = new HashMap<>();
        reqParams.put("text", text);
        reqParams.put("speaker", speaker);
        reqParams.put("audio_params", audioParams);
        reqParams.put(
                "additions",
                objectMapper.writeValueAsString(Map.of("disable_markdown_filter", true)));

        Map<String, Object> body = new HashMap<>();
        body.put("user", Map.of("uid", "aims-interview"));
        body.put("namespace", "BidirectionalTTS");
        body.put("req_params", reqParams);
        return objectMapper.writeValueAsString(body);
    }

    /** 截断字符串用于日志输出。 */
    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
