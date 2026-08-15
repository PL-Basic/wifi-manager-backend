package com.plagod.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.request.RequestId;
import com.plagod.service.GatewayValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.ConnectException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GatewayErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayErrorResponseWriter writer =
            new GatewayErrorResponseWriter(objectMapper);

    @Test
    void rateLimitResponseCarriesStableContract() throws IOException {
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(
                RequestId.REQUEST_ATTRIBUTE,
                "request_01JABCDEF1234");

        writer.write(
                exchange,
                HttpStatus.TOO_MANY_REQUESTS,
                429,
                "请求过于频繁",
                17L).block();

        JsonNode body = body(exchange);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                exchange.getResponse().getStatusCode());
        assertEquals("17", exchange.getResponse().getHeaders()
                .getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(429, body.path("code").asInt());
        assertEquals("RATE_LIMITED", body.path("errorKey").asText());
        assertEquals(
                "request_01JABCDEF1234",
                body.path("requestId").asText());
        assertEquals(
                body.path("requestId").asText(),
                exchange.getResponse().getHeaders()
                        .getFirst(RequestId.HEADER_NAME));
    }

    @Test
    void validationWriterKeepsFrozenBadRequestDefault()
            throws IOException {
        MockServerWebExchange exchange = exchange();

        writer.write(
                exchange,
                HttpStatus.BAD_REQUEST,
                400,
                "参数校验失败",
                null).block();

        JsonNode body = body(exchange);
        assertEquals(
                "VALIDATION_FAILED",
                body.path("errorKey").asText());
    }

    @Test
    void webFluxBadRequestUsesMalformedRequestError()
            throws IOException {
        MockServerWebExchange exchange = exchange();
        GatewayWebExceptionHandler handler =
                new GatewayWebExceptionHandler(writer);

        handler.handle(
                exchange,
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "canary-parser-detail")).block();

        String raw = exchange.getResponse().getBodyAsString().block();
        JsonNode body = objectMapper.readTree(raw);
        assertEquals(
                "MALFORMED_REQUEST",
                body.path("errorKey").asText());
        assertFalse(raw.contains("canary-parser-detail"));
    }

    @Test
    void unhandledExceptionDoesNotLeakItsMessage() throws IOException {
        MockServerWebExchange exchange = exchange();
        GatewayWebExceptionHandler handler =
                new GatewayWebExceptionHandler(writer);

        handler.handle(
                exchange,
                new RuntimeException("canary-provider-secret")).block();

        String raw = exchange.getResponse().getBodyAsString().block();
        JsonNode body = objectMapper.readTree(raw);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                exchange.getResponse().getStatusCode());
        assertEquals("INTERNAL_ERROR", body.path("errorKey").asText());
        assertFalse(raw.contains("canary-provider-secret"));
        assertNotNull(body.path("requestId").textValue());
    }

    @Test
    void dependencyExceptionUsesSafeServiceUnavailableMessage()
            throws IOException {
        MockServerWebExchange exchange = exchange();
        GatewayWebExceptionHandler handler =
                new GatewayWebExceptionHandler(writer);

        handler.handle(
                exchange,
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "canary-internal-endpoint")).block();

        String raw = exchange.getResponse().getBodyAsString().block();
        JsonNode body = objectMapper.readTree(raw);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                exchange.getResponse().getStatusCode());
        assertEquals(
                "DEPENDENCY_UNAVAILABLE",
                body.path("errorKey").asText());
        assertFalse(raw.contains("canary-internal-endpoint"));
    }

    @Test
    void wrappedConnectionFailureMapsToDependencyUnavailable()
            throws IOException {
        MockServerWebExchange exchange = exchange();
        GatewayWebExceptionHandler handler =
                new GatewayWebExceptionHandler(writer);

        handler.handle(
                exchange,
                new IllegalStateException(
                        "canary-wrapper-detail",
                        new ConnectException(
                                "canary-private-upstream"))).block();

        String raw = exchange.getResponse().getBodyAsString().block();
        JsonNode body = objectMapper.readTree(raw);
        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exchange.getResponse().getStatusCode());
        assertEquals(
                "DEPENDENCY_UNAVAILABLE",
                body.path("errorKey").asText());
        assertFalse(raw.contains("canary"));
    }

    @ParameterizedTest
    @MethodSource("statusCases")
    void frozenStatusMatrixUsesStableErrorKeys(
            HttpStatus status,
            String expectedErrorKey) throws IOException {
        MockServerWebExchange exchange = exchange();

        writer.write(
                exchange,
                status,
                status.value(),
                null,
                null).block();

        JsonNode body = body(exchange);
        assertEquals(status, exchange.getResponse().getStatusCode());
        assertEquals(expectedErrorKey, body.path("errorKey").asText());
        assertNotNull(body.path("requestId").textValue());
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            assertEquals(
                    "1",
                    exchange.getResponse().getHeaders()
                            .getFirst(HttpHeaders.RETRY_AFTER));
        }
    }

    @Test
    void classifiedGatewayExceptionKeepsSessionExpiredSemantics()
            throws IOException {
        MockServerWebExchange exchange = exchange();
        GatewayWebExceptionHandler handler =
                new GatewayWebExceptionHandler(writer);

        handler.handle(
                exchange,
                new GatewayValidationException(
                        401,
                        401,
                        "SESSION_EXPIRED",
                        "登录会话已经失效")).block();

        JsonNode body = body(exchange);
        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange.getResponse().getStatusCode());
        assertEquals(
                "SESSION_EXPIRED",
                body.path("errorKey").asText());
    }

    private static Stream<Arguments> statusCases() {
        return Stream.of(
                Arguments.of(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED"),
                Arguments.of(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED"),
                Arguments.of(
                        HttpStatus.FORBIDDEN,
                        "PERMISSION_DENIED"),
                Arguments.of(
                        HttpStatus.NOT_FOUND,
                        "RESOURCE_NOT_FOUND"),
                Arguments.of(
                        HttpStatus.CONFLICT,
                        "RESOURCE_VERSION_CONFLICT"),
                Arguments.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMITED"),
                Arguments.of(
                        HttpStatus.BAD_GATEWAY,
                        "DEPENDENCY_PROTOCOL_INVALID"),
                Arguments.of(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "DEPENDENCY_UNAVAILABLE"),
                Arguments.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR"));
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/missing").build());
    }

    private JsonNode body(MockServerWebExchange exchange)
            throws IOException {
        return objectMapper.readTree(
                exchange.getResponse().getBodyAsString().block());
    }
}
