package com.aims.core.common;

import java.io.Serializable;

/**
 * API 层统一响应体。仅用于 Controller 边界；模块内部调用一律抛异常，不包裹 Result。
 *
 * @param code 业务码，0 表示成功，其余见 {@link ErrorCode}
 * @param message 面向调用方的提示信息
 * @param data 业务数据
 * @param traceId 链路追踪 ID（P7 接入 OpenTelemetry 后由 MDC 填充）
 * @param timestamp 服务端时间戳（毫秒）
 */
public record Result<T>(int code, String message, T data, String traceId, long timestamp)
        implements Serializable {

    public static <T> Result<T> ok(T data) {
        return new Result<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(),
                data,
                TraceContext.traceId(),
                System.currentTimeMillis());
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(
                errorCode.getCode(),
                message,
                null,
                TraceContext.traceId(),
                System.currentTimeMillis());
    }
}
