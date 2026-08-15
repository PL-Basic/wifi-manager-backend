package com.plagod.filter;

import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRequestIdWebFilterTest {

    private final GatewayRequestIdWebFilter filter =
            new GatewayRequestIdWebFilter();

    @Test
    void validRequestIdIsNormalizedAndForwarded() {
        String inbound = "request_01JABCDEF1234";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/users/7")
                        .header(RequestId.HEADER_NAME, inbound)
                        .header(RequestId.HEADER_NAME, "duplicate_value_1234")
                        .build());
        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();
        AtomicReference<String> contextRequestId =
                new AtomicReference<>();

        filter.filter(
                exchange,
                current -> Mono.deferWithContext(context -> {
                    forwarded.set(current);
                    contextRequestId.set(context.get(
                            RequestId.REQUEST_ATTRIBUTE));
                    return Mono.empty();
                })).block();

        ServerWebExchange normalized = forwarded.get();
        assertNotNull(normalized);
        assertEquals(
                inbound,
                normalized.getAttribute(RequestId.REQUEST_ATTRIBUTE));
        assertEquals(
                1,
                normalized.getRequest().getHeaders()
                        .get(RequestId.HEADER_NAME).size());
        assertEquals(
                inbound,
                normalized.getRequest().getHeaders()
                        .getFirst(RequestId.HEADER_NAME));
        assertEquals(
                inbound,
                normalized.getResponse().getHeaders()
                        .getFirst(RequestId.HEADER_NAME));
        assertEquals(inbound, contextRequestId.get());
    }

    @Test
    void invalidRequestIdIsReplacedWithoutEchoingInput() {
        String forged = "forged request id with spaces";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/health/gateway")
                        .header(RequestId.HEADER_NAME, forged)
                        .build());
        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();
        AtomicReference<String> contextRequestId =
                new AtomicReference<>();

        filter.filter(
                exchange,
                current -> Mono.deferWithContext(context -> {
                    forwarded.set(current);
                    contextRequestId.set(context.get(
                            RequestId.REQUEST_ATTRIBUTE));
                    return Mono.empty();
                })).block();

        String generated = forwarded.get().getRequest().getHeaders()
                .getFirst(RequestId.HEADER_NAME);
        assertTrue(RequestId.isValid(generated));
        assertNotEquals(forged, generated);
        assertEquals(
                generated,
                forwarded.get().getResponse().getHeaders()
                        .getFirst(RequestId.HEADER_NAME));
        assertEquals(generated, contextRequestId.get());
    }
}
