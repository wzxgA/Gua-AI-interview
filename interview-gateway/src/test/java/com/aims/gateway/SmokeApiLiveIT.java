package com.aims.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 冒烟接口 Live 集成测试：连通真实模型与基础设施。
 *
 * <p>默认跳过（CI 无 API Key）；本地验证时设置环境变量 {@code AIMS_LIVE_TEST=true} 后执行 {@code mvn verify
 * -Dit.test=SmokeApiLiveIT}。
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "AIMS_LIVE_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmokeApiLiveIT {

    @LocalServerPort private int port;

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void blockingChatReturnsRealModelResponse() {
        String body =
                restTemplate.getForObject(
                        "http://localhost:"
                                + port
                                + "/api/smoke/chat?tier=STANDARD&prompt=用一句话介绍你自己",
                        String.class);

        assertThat(body).contains("\"code\":0").contains("\"data\":");
    }

    @Test
    void entityEndpointReturnsStructuredOutput() {
        String body =
                restTemplate.getForObject(
                        "http://localhost:" + port + "/api/smoke/entity", String.class);

        assertThat(body).contains("\"code\":0").contains("topic").contains("keyPoints");
    }

    @Test
    void infraEndpointAggregatesAllComponents() {
        String body =
                restTemplate.getForObject(
                        "http://localhost:" + port + "/api/smoke/infra", String.class);

        assertThat(body)
                .contains("postgres")
                .contains("redis")
                .contains("kafka")
                .contains("minio")
                .contains("\"vectorExtension\":true");
    }
}
