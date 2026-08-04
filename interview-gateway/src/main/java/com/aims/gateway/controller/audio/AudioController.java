package com.aims.gateway.controller.audio;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 音频代理控制器。
 *
 * <p>前端无法直连 MinIO，通过此控制器代理读取音频文件。 URL 格式：{@code /api/v1/audio/{bucket}/{objectName}}。
 */
@RestController
@RequestMapping("/api/v1/audio")
public class AudioController {

    private static final Logger log = LoggerFactory.getLogger(AudioController.class);

    private final MinioClient minioClient;

    public AudioController(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @GetMapping("/{bucket}/{*objectPath}")
    public ResponseEntity<StreamingResponseBody> getAudio(
            @PathVariable String bucket, @PathVariable String objectPath) {
        // objectPath 以 / 开头，去掉前导斜杠
        String objectName = objectPath.startsWith("/") ? objectPath.substring(1) : objectPath;

        try {
            InputStream inputStream =
                    minioClient.getObject(
                            GetObjectArgs.builder().bucket(bucket).object(objectName).build());

            StreamingResponseBody body =
                    out -> {
                        try (inputStream) {
                            inputStream.transferTo(out);
                        }
                    };

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .body(body);

        } catch (Exception e) {
            log.warn("音频文件不存在 bucket={} object={}", bucket, objectName, e);
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "音频文件不存在");
        }
    }
}
