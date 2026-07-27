package com.aims.core.common.exception;

import com.aims.core.common.ErrorCode;

/** 结构化输出解析失败（entity(type) 自修复后仍无法映射为目标类型）。 */
public class AiOutputParseException extends AiException {

    public AiOutputParseException(String message, Throwable cause) {
        super(ErrorCode.MODEL_OUTPUT_PARSE_FAILED, message, cause);
    }
}
