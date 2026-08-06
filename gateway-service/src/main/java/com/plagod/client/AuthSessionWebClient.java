package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.service.GatewayValidationException;
import com.plagod.vo.auth.SessionValidationVO;
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
public class AuthSessionWebClient {

    private static final ParameterizedTypeReference<ApiResponse<SessionValidationVO>> RESPONSE_TYPE =
            new ParameterizedTypeReference<ApiResponse<SessionValidationVO>>() { };

    private final WebClient webClient;
    private final String internalToken;
    private final Duration timeout;

    public AuthSessionWebClient(
                                @Qualifier("internalWebClientBuilder") WebClient.Builder internalWebClientBuilder,
                                @Value("${wifi.internal.token}") String internalToken,
                                @Value("${wifi.context-validation.timeout-millis:2000}") long timeoutMillis) {
        if (!StringUtils.hasText(internalToken)
                || internalToken.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("Gateway Internal Token 必须配置且不能少于16字节");
        }
        this.webClient = internalWebClientBuilder.baseUrl("http://auth-service").build();
        this.internalToken = internalToken;
        this.timeout = Duration.ofMillis(Math.max(200L, timeoutMillis));
    }

    public Mono<SessionValidationVO> validate(String sessionId, Long userId, String tokenId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/auth/sessions/{sessionId}/validate")
                        .queryParam("userId", userId)
                        .queryParam("jti", tokenId)
                        .build(sessionId))
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .onStatus(
                        status -> status.value() == 401 || status.value() == 403,
                        response -> Mono.error(new GatewayValidationException(
                                response.statusCode().value(),
                                response.statusCode().value(),
                                "登录会话已经失效")))
                .onStatus(
                        HttpStatus::isError,
                        response -> Mono.error(new GatewayValidationException(
                                503, 503, "认证会话校验服务暂时不可用")))
                .bodyToMono(RESPONSE_TYPE)
                .timeout(timeout)
                .onErrorMap(
                        throwable -> !(throwable instanceof GatewayValidationException),
                        throwable -> new GatewayValidationException(
                                503, 503, "认证会话校验服务暂时不可用"))
                .flatMap(response -> {
                    if (response == null || response.getCode() != 200 || response.getData() == null) {
                        return Mono.error(new GatewayValidationException(
                                503, 503, "认证会话校验服务返回无效结果"));
                    }
                    return Mono.just(response.getData());
                });
    }
}
