package com.plagod.web;

import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.request.RequestId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

/**
 * 构造 HTTP status、兼容 code、errorKey 和 requestId 一致的安全错误响应。
 */
public class ApiErrorResponseFactory {

    public <T> ResponseEntity<ApiResponse<T>> error(
            int httpStatus,
            int code,
            String errorKey,
            String message,
            T data,
            Long retryAfterSeconds) {

        HttpStatus status = HttpStatus.resolve(httpStatus);
        if (status == null || !status.isError()) {
            throw new IllegalArgumentException(
                    "错误响应必须使用有效的 4xx 或 5xx HTTP 状态");
        }

        String requestId = RequestIdContext.currentOrGenerate();
        ApiResponse<T> body = ApiResponse.error(
                code,
                safeMessage(status, message),
                data,
                ApiErrorKey.requireValid(errorKey),
                requestId);

        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(status)
                .header(RequestId.HEADER_NAME, requestId);

        Long safeRetryAfter = retryAfterSeconds;
        if (status == HttpStatus.TOO_MANY_REQUESTS
                && safeRetryAfter == null) {
            safeRetryAfter = 1L;
        }
        if (safeRetryAfter != null) {
            response.header(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(Math.max(1L, safeRetryAfter)));
        }

        return response.body(body);
    }

    private String safeMessage(HttpStatus status, String message) {
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return "请求处理失败";
        }
        if (status == HttpStatus.BAD_GATEWAY) {
            return "下游服务响应异常";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "依赖服务暂时不可用";
        }
        if (StringUtils.hasText(message)) {
            return message;
        }
        if (status == HttpStatus.UNAUTHORIZED) {
            return "需要登录后继续";
        }
        if (status == HttpStatus.FORBIDDEN) {
            return "无权执行当前操作";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "资源不存在";
        }
        if (status == HttpStatus.CONFLICT) {
            return "资源状态已经变化";
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return "请求过于频繁，请稍后再试";
        }
        return "请求参数无效";
    }
}
