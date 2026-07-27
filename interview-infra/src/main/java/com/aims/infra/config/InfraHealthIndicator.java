package com.aims.infra.config;

import io.minio.MinioClient;
import io.minio.messages.Bucket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * 基础设施连通性聚合健康指示器：PG（含 vector 扩展）/ Redis / Kafka / MinIO。
 *
 * <p>暴露于 {@code /actuator/health} 的 {@code infra} 节点，同时供冒烟接口 {@code /api/smoke/infra} 使用。
 */
@Component("infra")
public class InfraHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(InfraHealthIndicator.class);
    private static final long CHECK_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final KafkaAdmin kafkaAdmin;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public InfraHealthIndicator(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            KafkaAdmin kafkaAdmin,
            MinioClient minioClient,
            MinioProperties minioProperties) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.kafkaAdmin = kafkaAdmin;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new ConcurrentHashMap<>();
        boolean postgres = check(details, "postgres", this::checkPostgres);
        boolean redis = check(details, "redis", this::checkRedis);
        boolean kafka = check(details, "kafka", this::checkKafka);
        boolean minio = check(details, "minio", this::checkMinio);
        boolean allUp = postgres && redis && kafka && minio;
        return (allUp ? Health.up() : Health.down()).withDetails(details).build();
    }

    /** 执行单项检查：true=UP；异常时记录 WARN 并把错误写入 details。 */
    private boolean check(
            Map<String, Object> details, String name, Supplier<Map<String, Object>> checker) {
        try {
            Map<String, Object> detail = new ConcurrentHashMap<>(checker.get());
            detail.put("status", "UP");
            details.put(name, detail);
            return true;
        } catch (Exception e) {
            log.warn("基础设施连通性检查失败 {}: {}", name, e.toString());
            details.put(name, Map.of("status", "DOWN", "error", e.toString()));
            return false;
        }
    }

    private Map<String, Object> checkPostgres() {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            boolean vectorEnabled = false;
            try (ResultSet rs =
                    stmt.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'vector'")) {
                vectorEnabled = rs.next();
            }
            return Map.of("database", conn.getCatalog(), "vectorExtension", vectorEnabled);
        } catch (Exception e) {
            throw new IllegalStateException("PostgreSQL 连接失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> checkRedis() {
        String pong = redisConnectionFactory.getConnection().ping();
        return Map.of("ping", pong == null ? "UNKNOWN" : pong);
    }

    private Map<String, Object> checkKafka() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var cluster = admin.describeCluster();
            String clusterId = cluster.clusterId().get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int nodes = cluster.nodes().get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS).size();
            return Map.of("clusterId", clusterId == null ? "-" : clusterId, "nodes", nodes);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 连接失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> checkMinio() {
        List<String> buckets;
        try {
            buckets = minioClient.listBuckets().stream().map(Bucket::name).toList();
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 连接失败: " + e.getMessage(), e);
        }
        List<String> missing =
                minioProperties.requiredBuckets().stream()
                        .filter(required -> !buckets.contains(required))
                        .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("缺少预留 bucket: " + missing);
        }
        return Map.of("buckets", buckets);
    }
}
