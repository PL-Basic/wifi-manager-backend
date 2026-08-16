package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.service.GatewayValidationException;
import com.plagod.vo.tenant.TenantContextVO;
import com.plagod.vo.tenant.TenantContextValidationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class TenantContextWebClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantContextWebClient.class);
    private static final ParameterizedTypeReference<ApiResponse<TenantContextVO>> RESOLVE_TYPE =
            new ParameterizedTypeReference<ApiResponse<TenantContextVO>>() { };
    private static final ParameterizedTypeReference<ApiResponse<TenantContextValidationVO>> VALIDATE_TYPE =
            new ParameterizedTypeReference<ApiResponse<TenantContextValidationVO>>() { };

    private final WebClient webClient;
    private final String internalToken;
    private final Duration timeout;

    public TenantContextWebClient(
                                  @Qualifier("internalWebClientBuilder") WebClient.Builder internalWebClientBuilder,
                                  @Value("${wifi.internal.token}") String internalToken,
                                  @Value("${wifi.context-validation.timeout-millis:2000}") long timeoutMillis) {
        if (!StringUtils.hasText(internalToken)
                || internalToken.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("Gateway Internal Token 必须配置且不能少于16字节");
        }
        this.webClient = internalWebClientBuilder.baseUrl("http://tenant-service").build();
        this.internalToken = internalToken;
        this.timeout = Duration.ofMillis(Math.max(200L, timeoutMillis));
    }

    public Mono<TenantContextVO> resolve(TenantContextResolveRequest request) {
        return webClient.post()
                .uri("/internal/tenants/context/resolve")
                .header("X-Internal-Token", internalToken)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        HttpStatus::isError,
                        response -> mapStatus("resolve", response.statusCode().value()))
                .bodyToMono(RESOLVE_TYPE)
                .timeout(timeout)
                .retryWhen(timeoutRetry("resolve"))
                .onErrorMap(
                        throwable -> !(throwable instanceof GatewayValidationException),
                        throwable -> unavailable("resolve", throwable))
                .flatMap(response -> {
                    if (response == null || response.getCode() != 200 || response.getData() == null) {
                        logInvalidResponse("resolve", response);
                        return Mono.error(unavailable());
                    }
                    return Mono.just(response.getData());
                });
    }

    public Mono<TenantContextVO> validate(TenantContextValidationRequest request) {
        return webClient.post()
                .uri("/internal/tenants/context/validate")
                .header("X-Internal-Token", internalToken)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        HttpStatus::isError,
                        response -> mapStatus("validate", response.statusCode().value()))
                .bodyToMono(VALIDATE_TYPE)
                .timeout(timeout)
                .retryWhen(timeoutRetry("validate"))
                .onErrorMap(
                        throwable -> !(throwable instanceof GatewayValidationException),
                        throwable -> unavailable("validate", throwable))
                .flatMap(response -> {
                    if (response == null || response.getCode() != 200 || response.getData() == null
                            || !Boolean.TRUE.equals(response.getData().getAllowed())
                            || response.getData().getContext() == null) {
                        LOGGER.warn(
                                "gateway tenant context validate returned invalid response: "
                                        + "responsePresent={}, code={}, dataPresent={}, "
                                        + "allowed={}, contextPresent={}",
                                response != null,
                                response == null ? null : response.getCode(),
                                response != null && response.getData() != null,
                                response != null && response.getData() != null
                                        ? response.getData().getAllowed() : null,
                                response != null && response.getData() != null
                                        && response.getData().getContext() != null);
                        return Mono.error(new GatewayValidationException(
                                403, 403, "租户上下文无效"));
                    }
                    return Mono.just(response.getData().getContext());
                });
    }

    private Retry timeoutRetry(String operation) {
        return Retry.max(1)
                .filter(TimeoutException.class::isInstance)
                .doBeforeRetry(signal -> LOGGER.warn(
                        "gateway tenant context {} retrying once after timeout",
                        operation))
                .onRetryExhaustedThrow((retrySpec, signal) -> signal.failure());
    }

    private Mono<? extends Throwable> mapStatus(String operation, int status) {
        LOGGER.warn(
                "gateway tenant context {} rejected: downstreamStatus={}",
                operation,
                status);
        if (status == 400) {
            return Mono.just(new GatewayValidationException(401, 401, "租户上下文内容无效"));
        }
        if (status == 401 || status == 403 || status == 404) {
            return Mono.just(new GatewayValidationException(
                    status == 404 ? 403 : status,
                    status == 404 ? 403 : status,
                    "租户上下文已经失效"));
        }
        return Mono.just(unavailable());
    }

    private GatewayValidationException unavailable(String operation, Throwable throwable) {
        LOGGER.warn(
                "gateway tenant context {} failed before response: exception={}",
                operation,
                throwable.getClass().getSimpleName());
        return unavailable();
    }

    private void logInvalidResponse(String operation, ApiResponse<?> response) {
        LOGGER.warn(
                "gateway tenant context {} returned invalid response: "
                        + "responsePresent={}, code={}, dataPresent={}",
                operation,
                response != null,
                response == null ? null : response.getCode(),
                response != null && response.getData() != null);
    }

    private GatewayValidationException unavailable() {
        return new GatewayValidationException(503, 503, "租户上下文校验服务暂时不可用");
    }
}
