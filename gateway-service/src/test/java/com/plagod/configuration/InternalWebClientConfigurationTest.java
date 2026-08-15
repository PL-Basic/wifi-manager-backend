package com.plagod.configuration;

import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalWebClientConfigurationTest {

    @Test
    void missingContextGeneratesSafeRequestId() {
        String forwarded = execute(null);

        assertTrue(RequestId.isValid(forwarded));
    }

    @Test
    void invalidContextIsReplacedWithoutEchoingInput() {
        String forged = "forged request id with spaces";
        String forwarded = execute(forged);

        assertTrue(RequestId.isValid(forwarded));
        assertNotEquals(forged, forwarded);
    }

    private String execute(String contextRequestId) {
        AtomicReference<String> forwarded = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            forwarded.set(request.headers().getFirst(
                    RequestId.HEADER_NAME));
            return Mono.just(ClientResponse.create(
                    HttpStatus.OK).build());
        };
        WebClient client = WebClient.builder()
                .filter(new InternalWebClientConfiguration()
                        .internalRequestIdExchangeFilter())
                .exchangeFunction(exchange)
                .build();

        Mono<ClientResponse> response = client.get()
                .uri("/internal/test")
                .exchange();
        if (contextRequestId != null) {
            response = response.subscriberContext(context -> context.put(
                    RequestId.REQUEST_ATTRIBUTE,
                    contextRequestId));
        }
        response.block();
        return forwarded.get();
    }
}
