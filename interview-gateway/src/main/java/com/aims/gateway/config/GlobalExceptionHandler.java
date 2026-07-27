package com.aims.gateway.config;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.Result;
import com.aims.core.common.exception.AiException;
import com.aims.core.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理：统一错误体 + WARN/ERROR 分级日志。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常（含 AI 异常）。AI 上游故障映射为 502，其余业务异常为 400。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        log.warn("业务异常 code={} message={}", e.getErrorCode().getCode(), e.getMessage());
        HttpStatus status =
                e instanceof AiException ? HttpStatus.BAD_GATEWAY : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    /**
     * @RequestBody / @ModelAttribute 校验失败。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String message =
                e.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return badRequest(ErrorCode.PARAM_INVALID, message);
    }

    /** 方法级参数校验失败（@Validated + @RequestParam 等，Spring 6.1+）。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        String message =
                e.getAllValidationResults().stream()
                        .flatMap(result -> result.getResolvableErrors().stream())
                        .map(
                                error ->
                                        error.getDefaultMessage() == null
                                                ? error.toString()
                                                : error.getDefaultMessage())
                        .distinct()
                        .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return badRequest(ErrorCode.PARAM_INVALID, message);
    }

    /** 约束违反（Service 层 @Validated 抛出）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message =
                e.getConstraintViolations().stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return badRequest(ErrorCode.PARAM_INVALID, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(
            MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return badRequest(ErrorCode.PARAM_MISSING, "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String required = e.getRequiredType() == null ? "-" : e.getRequiredType().getSimpleName();
        String message = "参数 " + e.getName() + " 应为 " + required + "，当前值: " + e.getValue();
        log.warn("参数类型不匹配: {}", message);
        return badRequest(ErrorCode.PARAM_TYPE_MISMATCH, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ErrorCode.RESOURCE_NOT_FOUND, "资源不存在: " + e.getResourcePath()));
    }

    /** 兜底。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("系统内部错误", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<Result<Void>> badRequest(ErrorCode errorCode, String message) {
        return ResponseEntity.badRequest().body(Result.fail(errorCode, message));
    }
}
