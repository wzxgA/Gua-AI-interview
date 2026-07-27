package com.aims.gateway;

import com.aims.core.common.TraceContext;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** AIMS 启动应用（仅 gateway 模块允许存在启动类与 Web 层）。 */
@SpringBootApplication(scanBasePackages = "com.aims")
public class Application {

    public static void main(String[] args) {
        // 为 core 安装 traceId 提供方；P7 接入 OpenTelemetry 后替换为 OTel 实现
        TraceContext.register(() -> MDC.get("traceId"));
        SpringApplication.run(Application.class, args);
    }
}
