package com.plagod.web;

import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.ApiStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务本地 Advice 可继承或委托此类；本类不注册为 Advice，避免双重异常映射。
 */
public class ServletApiExceptionHandlerSupport {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ServletApiExceptionHandlerSupport.class);
    private static final String INVALID_FIELD_MESSAGE = "参数不符合要求";

    private final ApiErrorResponseFactory responseFactory;

    public ServletApiExceptionHandlerSupport(
            ApiErrorResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<String>>>
    handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {

        List<String> errors = new ArrayList<>();
        for (FieldError fieldError
                : exception.getBindingResult().getFieldErrors()) {
            errors.add(safeField(fieldError.getField()));
        }
        return responseFactory.error(
                400,
                400,
                ApiErrorKey.VALIDATION_FAILED.value(),
                "参数校验失败",
                errors,
                null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<String>>>
    handleConstraintViolationException(
            ConstraintViolationException exception) {

        List<String> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation
                : exception.getConstraintViolations()) {
            errors.add(safeField(lastPathNode(violation.getPropertyPath())));
        }
        return responseFactory.error(
                400,
                400,
                ApiErrorKey.VALIDATION_FAILED.value(),
                "参数校验失败",
                errors,
                null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        return responseFactory.error(
                400,
                400,
                ApiErrorKey.MALFORMED_REQUEST.value(),
                "请求体不是有效的 JSON 或字段类型不正确",
                null,
                null);
    }

    @ExceptionHandler({
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>>
    handleRequestBindingException(Exception exception) {
        return responseFactory.error(
                400,
                400,
                ApiErrorKey.MALFORMED_REQUEST.value(),
                "请求参数格式错误或缺少必要参数",
                null,
                null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleIllegalArgumentException(IllegalArgumentException exception) {
        return responseFactory.error(
                400,
                400,
                ApiErrorKey.VALIDATION_FAILED.value(),
                "请求参数无效",
                null,
                null);
    }

    @ExceptionHandler(ApiStatusException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleApiStatusException(ApiStatusException exception) {
        return responseFactory.error(
                exception.getHttpStatus(),
                exception.getCode(),
                exception.getErrorKey(),
                exception.getMessage(),
                null,
                exception.getRetryAfterSeconds());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>>
    handleUnexpectedException(Exception exception) {
        ResponseEntity<ApiResponse<Void>> response = responseFactory.error(
                500,
                500,
                ApiErrorKey.INTERNAL_ERROR.value(),
                "请求处理失败",
                null,
                null);
        LOGGER.error(
                "unhandled API exception: requestId={}, type={}, safeStack={}",
                response.getBody() == null
                        ? null
                        : response.getBody().getRequestId(),
                exception.getClass().getName(),
                SafeExceptionLogFormatter.format(exception));
        return response;
    }

    private String safeField(String field) {
        String normalized = field == null
                ? ""
                : field.replaceAll("[^A-Za-z0-9_.\\[\\]-]", "");
        if (normalized.isEmpty()) {
            return INVALID_FIELD_MESSAGE;
        }
        return normalized + ": " + INVALID_FIELD_MESSAGE;
    }

    private String lastPathNode(Path path) {
        String last = null;
        if (path != null) {
            for (Path.Node node : path) {
                if (node != null && node.getName() != null) {
                    last = node.getName();
                }
            }
        }
        return last;
    }
}
