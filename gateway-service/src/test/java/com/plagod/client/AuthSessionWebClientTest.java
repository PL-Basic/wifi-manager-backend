package com.plagod.client;

import com.plagod.service.GatewayValidationException;
import com.plagod.vo.auth.SessionValidationVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSessionWebClientTest {

    private static final String INTERNAL_TOKEN = "0123456789abcdef";

    @Test
    void retriesOneTransportTimeoutForIdempotentValidation() {
        AtomicInteger attempts = new AtomicInteger();
        ExchangeFunction exchange = request -> attempts.incrementAndGet() == 1
                ? Mono.error(new TimeoutException("stale pooled connection"))
                : Mono.just(successResponse());
        AuthSessionWebClient client = client(exchange);

        SessionValidationVO result =
                client.validate("session-id", 7L, "access-jti").block();

        assertEquals(2, attempts.get());
        assertTrue(Boolean.TRUE.equals(result.getActive()));
        assertEquals("session-id", result.getSessionId());
    }

    @Test
    void doesNotRetryDownstreamHttpFailure() {
        AtomicInteger attempts = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            attempts.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
        };
        AuthSessionWebClient client = client(exchange);

        GatewayValidationException exception = assertThrows(
                GatewayValidationException.class,
                () -> client.validate("session-id", 7L, "access-jti").block());

        assertEquals(1, attempts.get());
        assertEquals(503, exception.getHttpStatus());
    }

    private AuthSessionWebClient client(ExchangeFunction exchange) {
        return new AuthSessionWebClient(
                WebClient.builder().exchangeFunction(exchange),
                INTERNAL_TOKEN,
                2000L);
    }

    private ClientResponse successResponse() {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"code\":200,\"message\":\"ok\",\"data\":{"
                        + "\"active\":true,\"status\":\"ACTIVE\","
                        + "\"sessionId\":\"session-id\",\"userId\":\"7\"}}")
                .build();
    }
}
