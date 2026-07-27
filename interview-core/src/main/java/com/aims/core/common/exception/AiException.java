package com.aims.core.common.exception;

import com.aims.core.common.ErrorCode;

/** AI 链路异常（模型调用失败、限流、档位不支持等）。 */
public class AiException extends BizException {

    public AiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AiException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AiException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
