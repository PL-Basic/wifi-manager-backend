package com.plagod.web;

import com.plagod.exception.ApiErrorKey;
import com.plagod.service.GatewayValidationException;
import com.plagod.support.SafeExceptionLogFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GatewayWebExceptionHandler implements WebExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GatewayWebExceptionHandler.class);

    private final GatewayErrorResponseWriter errorResponseWriter;

    public GatewayWebExceptionHandler(
            GatewayErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        if (exception instanceof GatewayValidationException) {
            GatewayValidationException validationException =
                    (GatewayValidationException) exception;
            HttpStatus status = HttpStatus.resolve(
                    validationException.getHttpStatus());
            HttpStatus safeStatus = status == null
                    ? HttpStatus.INTERNAL_SERVER_ERROR : status;
            return errorResponseWriter.write(
                    exchange,
                    safeStatus,
                    validationException.getCode(),
                    validationException.getErrorKey(),
                    validationException.getMessage(),
                    null);
        }

        HttpStatus status = resolveStatus(exception);
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            LOGGER.error(
                    "unhandled gateway exception: requestId={}, type={}, "
                            + "safeStack={}",
                    errorResponseWriter.requestId(exchange),
                    exception.getClass().getName(),
                    SafeExceptionLogFormatter.format(exception));
        }

        return errorResponseWriter.write(
                exchange,
                status,
                status.value(),
                status == HttpStatus.BAD_REQUEST
                        ? ApiErrorKey.MALFORMED_REQUEST.value()
                        : ApiErrorKey.defaultForHttpStatus(
                                status.value()).value(),
                defaultMessage(status),
                null);
    }

    private HttpStatus resolveStatus(Throwable exception) {
        if (exception instanceof ResponseStatusException) {
            HttpStatus status =
                    ((ResponseStatusException) exception).getStatus();
            if (status == HttpStatus.BAD_REQUEST
                    || status == HttpStatus.UNAUTHORIZED
                    || status == HttpStatus.FORBIDDEN
                    || status == HttpStatus.NOT_FOUND
                    || status == HttpStatus.CONFLICT
                    || status == HttpStatus.TOO_MANY_REQUESTS
                    || status == HttpStatus.BAD_GATEWAY
                    || status == HttpStatus.SERVICE_UNAVAILABLE) {
                return status;
            }
            if (status.is4xxClientError()) {
                return HttpStatus.BAD_REQUEST;
            }
        }
        if (hasCause(
                exception,
                ConnectException.class,
                SocketTimeoutException.class,
                TimeoutException.class)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    @SafeVarargs
    private final boolean hasCause(
            Throwable exception,
            Class<? extends Throwable>... types) {
        Throwable current = exception;
        int depth = 0;
        while (current != null && depth < 8) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(current)) {
                    return true;
                }
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
            depth++;
        }
        return false;
    }

    private String defaultMessage(HttpStatus status) {
        switch (status) {
            case BAD_REQUEST:
                return "请求格式或参数无效";
            case UNAUTHORIZED:
                return "请先登录";
            case FORBIDDEN:
                return "无权访问该资源";
            case NOT_FOUND:
                return "请求的资源不存在";
            case CONFLICT:
                return "资源状态已变化，请刷新后重试";
            case TOO_MANY_REQUESTS:
                return "请求过于频繁，请稍后重试";
            default:
                return null;
        }
    }
}
