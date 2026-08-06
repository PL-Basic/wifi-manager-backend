package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.tenant.TenantContextResolveRequest;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.service.GatewayValidationException;
import com.plagod.vo.tenant.TenantContextVO;
import com.plagod.vo.tenant.TenantContextValidationVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class TenantContextWebClient {

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
                .onStatus(HttpStatus::isError, response -> mapStatus(response.statusCode().value()))
                .bodyToMono(RESOLVE_TYPE)
                .timeout(timeout)
                .onErrorMap(
                        throwable -> !(throwable instanceof GatewayValidationException),
                        throwable -> unavailable())
                .flatMap(response -> {
                    if (response == null || response.getCode() != 200 || response.getData() == null) {
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
                .onStatus(HttpStatus::isError, response -> mapStatus(response.statusCode().value()))
                .bodyToMono(VALIDATE_TYPE)
                .timeout(timeout)
                .onErrorMap(
                        throwable -> !(throwable instanceof GatewayValidationException),
                        throwable -> unavailable())
                .flatMap(response -> {
                    if (response == null || response.getCode() != 200 || response.getData() == null
                            || !Boolean.TRUE.equals(response.getData().getAllowed())
                            || response.getData().getContext() == null) {
                        return Mono.error(new GatewayValidationException(
                                403, 403, "租户上下文无效"));
                    }
                    return Mono.just(response.getData().getContext());
                });
    }

    private Mono<? extends Throwable> mapStatus(int status) {
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

    private GatewayValidationException unavailable() {
        return new GatewayValidationException(503, 503, "租户上下文校验服务暂时不可用");
    }
}
