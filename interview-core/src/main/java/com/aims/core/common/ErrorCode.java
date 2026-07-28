package com.aims.core.common;

/** 错误码枚举。号段约定：0 成功；1xxx 通用；2xxx AI；3xxx 基础设施。 P1 仅定义通用段与 AI 段，3xxx 预留少量基建连通性错误。 */
public enum ErrorCode {

    // ---- 通用 1xxx ----
    SUCCESS(0, "成功"),
    INTERNAL_ERROR(1000, "系统内部错误"),
    PARAM_INVALID(1001, "参数校验失败"),
    PARAM_MISSING(1002, "缺少必要参数"),
    PARAM_TYPE_MISMATCH(1003, "参数类型不匹配"),
    RESOURCE_NOT_FOUND(1004, "资源不存在"),

    // ---- AI 2xxx ----
    MODEL_CALL_FAILED(2001, "模型调用失败"),
    MODEL_OUTPUT_PARSE_FAILED(2002, "模型输出解析失败"),
    MODEL_RATE_LIMITED(2003, "模型触发限流"),
    MODEL_TIER_UNSUPPORTED(2004, "不支持的模型档位"),

    // ---- 基础设施 3xxx ----
    INFRA_UNAVAILABLE(3001, "基础设施不可用"),
    REDIS_CONNECT_FAILED(3002, "Redis 连接失败"),
    KAFKA_CONNECT_FAILED(3003, "Kafka 连接失败"),
    MINIO_CONNECT_FAILED(3004, "MinIO 连接失败"),

    // ---- 业务 4xxx（P2 起） ----
    RESUME_PARSE_FAILED(4001, "简历解析失败"),
    EMBEDDING_FAILED(4002, "向量化失败"),
    RAG_SEARCH_FAILED(4003, "RAG 检索失败"),
    FILE_UPLOAD_FAILED(4004, "文件上传失败"),
    QUESTION_IMPORT_PARTIAL(4005, "题库批量导入部分失败"),

    // ---- 面试会话 4100+（P3） ----
    SESSION_NOT_FOUND(4101, "面试会话不存在"),
    SESSION_STATUS_CONFLICT(4102, "会话状态不允许该操作"),
    SESSION_PLAN_FAILED(4103, "面试计划生成失败"),
    SESSION_ROUND_CONFLICT(4104, "面试轮次冲突或已存在"),
    SESSION_LOCKED(4105, "会话已被其他连接占用"),
    SESSION_MESSAGE_INVALID(4106, "WebSocket 消息格式或内容非法");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
