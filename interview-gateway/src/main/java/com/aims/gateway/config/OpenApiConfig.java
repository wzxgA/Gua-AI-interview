package com.aims.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** OpenAPI 文档配置（仅 local/dev 开启，生产不暴露）。 */
@Profile({"local", "dev"})
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI aimsOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("瓜分Offer API")
                                .version("v1")
                                .description("瓜分Offer AI 智能面试 Agent 平台 API（P1：冒烟接口 + 健康检查）"));
    }
}
