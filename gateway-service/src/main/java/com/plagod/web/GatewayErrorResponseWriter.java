package com.plagod.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.request.RequestId;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class GatewayErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public GatewayErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            int code,
            String message,
            Long retryAfterSeconds) {
        return write(
                exchange,
                status,
                code,
                ApiErrorKey.defaultForHttpStatus(status.value()).value(),
                message,
                retryAfterSeconds);
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            int code,
            String errorKey,
            String message,
            Long retryAfterSeconds) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        String requestId = requestId(exchange);
        String resolvedErrorKey = ApiErrorKey.isValid(errorKey)
                ? errorKey
                : ApiErrorKey.defaultForHttpStatus(
                        status.value()).value();
        String resolvedMessage = safeMessage(status, message);
        byte[] body = serialize(
                code,
                resolvedMessage,
                resolvedErrorKey,
                requestId);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(
                MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        exchange.getResponse().getHeaders().set(
                RequestId.HEADER_NAME,
                requestId);

        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            long safeRetryAfter = retryAfterSeconds == null
                    ? 1L : Math.max(1L, retryAfterSeconds);
            exchange.getResponse().getHeaders().set(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(safeRetryAfter));
        }

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    public String requestId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(
                RequestId.REQUEST_ATTRIBUTE);
        String current = attribute instanceof String
                ? (String) attribute : null;
        if (!RequestId.isValid(current)) {
            current = exchange.getRequest().getHeaders()
                    .getFirst(RequestId.HEADER_NAME);
        }
        if (!RequestId.isValid(current)) {
            current = RequestId.generate();
        }

        exchange.getAttributes().put(
                RequestId.REQUEST_ATTRIBUTE,
                current);
        exchange.getResponse().getHeaders().set(
                RequestId.HEADER_NAME,
                current);
        return current;
    }

    private byte[] serialize(
            int code,
            String message,
            String errorKey,
            String requestId) {
        try {
            return objectMapper.writeValueAsBytes(ApiResponse.error(
                    code,
                    message,
                    null,
                    errorKey,
                    requestId));
        } catch (Exception exception) {
            String fallback = String.format(
                    Locale.ROOT,
                    "{\"code\":%d,\"message\":\"请求处理失败\","
                            + "\"data\":null,\"errorKey\":\"%s\","
                            + "\"requestId\":\"%s\"}",
                    code,
                    errorKey,
                    requestId);
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String safeMessage(HttpStatus status, String message) {
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return "系统繁忙，请稍后重试";
        }
        if (status == HttpStatus.BAD_GATEWAY) {
            return "上游服务返回异常";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "服务暂时不可用，请稍后重试";
        }
        return message == null || message.trim().isEmpty()
                ? "请求处理失败" : message;
    }

}
