package com.aims.infra.config;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** MinIO 连接配置（前缀 {@code aims.minio}）。 */
@Validated
@ConfigurationProperties(prefix = "aims.minio")
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        List<String> buckets) {

    public List<String> requiredBuckets() {
        return buckets == null ? List.of() : buckets;
    }
}
