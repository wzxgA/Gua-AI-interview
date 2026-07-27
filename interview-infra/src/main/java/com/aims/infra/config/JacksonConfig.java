package com.aims.infra.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 统一配置：
 *
 * <ul>
 *   <li>Long/long 序列化为字符串（避免 JS 精度丢失），以 {@link Module} Bean 方式注册， 由 Spring Boot 自动装配进全局
 *       ObjectMapper
 *   <li>JavaTimeModule 由 Spring Boot 自动注册
 *   <li>空值策略（non_null）见 application.yml {@code spring.jackson.default-property-inclusion}
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    Module longToStringModule() {
        SimpleModule module = new SimpleModule("aims-long-to-string");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}
