package com.plagod.configuration;

import com.plagod.client.AdminDownstreamException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminFeignErrorDecoderTest {

    private static final String BODY_CANARY =
            "CANARY_DOWNSTREAM_SECRET_BODY";

    private final AdminFeignErrorDecoder decoder =
            new AdminFeignErrorDecoder();

    @Test
    void shouldExtractSinglePositiveRetryAfterWithoutBody() {
        Response response = response(
                429,
                Collections.singletonMap(
                        "retry-after",
                        Collections.singletonList("23")));

        Exception decoded =
                decoder.decode("UserServiceClient#find", response);

        assertTrue(decoded instanceof AdminDownstreamException);
        AdminDownstreamException exception =
                (AdminDownstreamException) decoded;
        assertEquals(429, exception.getDownstreamStatus());
        assertEquals(Long.valueOf(23L),
                exception.getRetryAfterSeconds());
        assertFalse(exception.getMessage().contains(BODY_CANARY));
    }

    @Test
    void shouldPreserveFrozenFeignStatusesWithoutBody() {
        int[] statuses = {400, 404, 409, 429};

        for (int status : statuses) {
            AdminDownstreamException exception =
                    (AdminDownstreamException) decoder.decode(
                            "TenantServiceClient#call",
                            response(status, Collections.emptyMap()));

            assertEquals(status,
                    exception.getDownstreamStatus());
            assertNull(exception.getRetryAfterSeconds());
            assertFalse(exception.getMessage().contains(
                    BODY_CANARY));
        }
    }

    @Test
    void shouldRejectAmbiguousOrNonNumericRetryAfter() {
        Map<String, Collection<String>> headers =
                new LinkedHashMap<>();
        headers.put(
                HttpHeaders.RETRY_AFTER,
                java.util.Arrays.asList("7", "11"));
        assertNull(decodedRetryAfter(response(429, headers)));

        headers.clear();
        headers.put(
                HttpHeaders.RETRY_AFTER,
                Collections.singletonList("tomorrow"));
        assertNull(decodedRetryAfter(response(429, headers)));

        headers.clear();
        headers.put(
                HttpHeaders.RETRY_AFTER,
                Collections.singletonList("0"));
        assertNull(decodedRetryAfter(response(429, headers)));
    }

    private Long decodedRetryAfter(Response response) {
        return ((AdminDownstreamException) decoder.decode(
                "DeviceServiceClient#command",
                response)).getRetryAfterSeconds();
    }

    private Response response(
            int status,
            Map<String, Collection<String>> headers) {
        Request request = Request.create(
                "GET",
                "http://downstream.invalid/test",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8);
        return Response.builder()
                .status(status)
                .reason("downstream error")
                .headers(headers)
                .request(request)
                .body(BODY_CANARY, StandardCharsets.UTF_8)
                .build();
    }
}
