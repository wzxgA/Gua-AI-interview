package com.aims.core.common;

import java.util.function.Supplier;

/** 链路追踪上下文钩子。core 模块不依赖任何日志框架， 由启动模块（gateway）在启动时安装基于 MDC 的实现；P7 接入 OpenTelemetry 后替换为 OTel 实现。 */
public final class TraceContext {

    private static volatile Supplier<String> traceIdSupplier = () -> null;

    private TraceContext() {}

    /** 安装 traceId 提供方（应用启动时调用一次）。 */
    public static void register(Supplier<String> supplier) {
        traceIdSupplier = supplier;
    }

    /** 获取当前链路 traceId，未安装提供方或无上下文时返回 null。 */
    public static String traceId() {
        return traceIdSupplier.get();
    }
}
