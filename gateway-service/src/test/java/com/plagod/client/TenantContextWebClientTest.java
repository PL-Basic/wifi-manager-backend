package com.plagod.client;

import com.plagod.configuration.InternalWebClientConfiguration;
import com.plagod.dto.tenant.TenantContextValidationRequest;
import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantContextWebClientTest {

    private static final String INTERNAL_TOKEN = "0123456789abcdef";

    @Test
    void propagatesGatewayRequestIdToTenantValidation() {
        String requestId = "request_01JABCDEF1234";
        AtomicReference<String> forwarded = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            forwarded.set(request.headers().getFirst(
                    RequestId.HEADER_NAME));
            return Mono.just(successResponse());
        };
        TenantContextWebClient client = new TenantContextWebClient(
                WebClient.builder()
                        .filter(new InternalWebClientConfiguration()
                                .internalRequestIdExchangeFilter())
                        .exchangeFunction(exchange),
                INTERNAL_TOKEN,
                2000L);

        client.validate(new TenantContextValidationRequest())
                .subscriberContext(context -> context.put(
                        RequestId.REQUEST_ATTRIBUTE,
                        requestId))
                .block();

        assertEquals(requestId, forwarded.get());
    }

    private ClientResponse successResponse() {
        return ClientResponse.create(HttpStatus.OK)
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE)
                .body("{\"code\":200,\"message\":\"ok\",\"data\":{"
                        + "\"allowed\":true,\"context\":{"
                        + "\"contextType\":\"TENANT\","
                        + "\"tenantId\":\"11\"}}}")
                .build();
    }
}
