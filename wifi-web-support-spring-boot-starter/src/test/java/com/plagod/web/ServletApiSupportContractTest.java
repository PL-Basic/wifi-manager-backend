package com.plagod.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import com.plagod.security.TrustedHeaderNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServletApiSupportContractTest {

    private static final String FIXTURE =
            "/contracts/demo-1.4-s1/http-support-v1.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void factoryMatchesFrozenStatusesAndNeverLeaksServerMessages()
            throws Exception {
        JsonNode contract = readFixture();
        String requestId = contract.path("requestId").path("sample").asText();
        MDC.put(RequestIdContext.MDC_KEY, requestId);

        ApiErrorResponseFactory factory = new ApiErrorResponseFactory();
        for (JsonNode testCase : contract.path("statusCases")) {
            int status = testCase.path("status").asInt();
            Long retryAfter = testCase.has("retryAfterSeconds")
                    ? testCase.path("retryAfterSeconds").asLong()
                    : null;
            ResponseEntity<ApiResponse<Void>> response = factory.error(
                    status,
                    status,
                    testCase.path("errorKey").asText(),
                    "provider-secret-body",
                    null,
                    retryAfter);

            assertEquals(status, response.getStatusCodeValue());
            assertNotNull(response.getBody());
            assertEquals(status, response.getBody().getCode());
            assertEquals(
                    testCase.path("errorKey").asText(),
                    response.getBody().getErrorKey());
            assertEquals(requestId, response.getBody().getRequestId());
            assertEquals(
                    requestId,
                    response.getHeaders().getFirst(RequestId.HEADER_NAME));
            if (status >= 500) {
                assertFalse(
                        response.getBody().getMessage()
                                .contains("provider-secret-body"));
            }
            if (retryAfter != null) {
                assertEquals(
                        String.valueOf(retryAfter),
                        response.getHeaders().getFirst(
                                HttpHeaders.RETRY_AFTER));
            }
        }

        ResponseEntity<ApiResponse<Void>> defaultRateLimit =
                factory.error(
                        429,
                        429,
                        "RATE_LIMITED",
                        "请求过于频繁",
                        null,
                        null);
        assertEquals(
                "1",
                defaultRateLimit.getHeaders().getFirst(
                        HttpHeaders.RETRY_AFTER));
    }

    @Test
    void handlerUsesSafeMalformedAndUnknownResponses() {
        ServletApiExceptionHandlerSupport handler =
                new ServletApiExceptionHandlerSupport(
                        new ApiErrorResponseFactory());

        ResponseEntity<ApiResponse<Void>> malformed =
                handler.handleHttpMessageNotReadableException(
                        new HttpMessageNotReadableException(
                                "raw-password=secret"));
        assertEquals(400, malformed.getStatusCodeValue());
        assertEquals(
                "MALFORMED_REQUEST",
                malformed.getBody().getErrorKey());
        assertFalse(malformed.getBody().getMessage().contains("secret"));

        ResponseEntity<ApiResponse<Void>> unknown =
                handler.handleUnexpectedException(
                        new RuntimeException("token=secret"));
        assertEquals(500, unknown.getStatusCodeValue());
        assertEquals("INTERNAL_ERROR", unknown.getBody().getErrorKey());
        assertEquals("请求处理失败", unknown.getBody().getMessage());
        assertFalse(unknown.getBody().getMessage().contains("secret"));
    }

    @Test
    void handlerMapsEveryClassifiedStatusWithoutChangingHttpStatus()
            throws Exception {
        JsonNode contract = readFixture();
        ServletApiExceptionHandlerSupport handler =
                new ServletApiExceptionHandlerSupport(
                        new ApiErrorResponseFactory());

        for (JsonNode testCase : contract.path("statusCases")) {
            int status = testCase.path("status").asInt();
            if (status == 400 || status == 500) {
                continue;
            }
            Long retryAfter = testCase.has("retryAfterSeconds")
                    ? testCase.path("retryAfterSeconds").asLong()
                    : null;
            ApiStatusException exception = new ApiStatusException(
                    status,
                    status,
                    testCase.path("errorKey").asText(),
                    "classified message",
                    retryAfter);

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleApiStatusException(exception);

            assertEquals(status, response.getStatusCodeValue());
            assertEquals(status, response.getBody().getCode());
            assertEquals(
                    testCase.path("errorKey").asText(),
                    response.getBody().getErrorKey());
        }
    }

    @Test
    void filterAcceptsOnlyTrustedValidIdsAndAlwaysCleansMdc()
            throws Exception {
        String trusted = "request-id-00000001";
        assertEquals(trusted, runFilter(trusted, true));

        String forged = runFilter("forged-id-00000001", false);
        assertNotEquals("forged-id-00000001", forged);
        assertTrue(RequestId.isValid(forged));

        String invalid = runFilter("bad id", true);
        assertNotEquals("bad id", invalid);
        assertTrue(RequestId.isValid(invalid));
        assertNull(MDC.get(RequestIdContext.MDC_KEY));
    }

    @Test
    void sharedSupportDoesNotRegisterASecondControllerAdvice()
            throws Exception {
        JsonNode contract = readFixture();
        assertEquals("http-support-v1", contract.path("version").asText());
        assertEquals("http-envelope-v1", contract.path("envelopeVersion").asText());
        assertFalse(
                ServletApiExceptionHandlerSupport.class
                        .isAnnotationPresent(RestControllerAdvice.class));
    }

    private String runFilter(String inbound, boolean trusted)
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestId.HEADER_NAME, inbound);
        if (trusted) {
            request.setAttribute(
                    TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE,
                    TrustedHeaderNames.SOURCE_GATEWAY);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();

        new RequestIdFilter().doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    HttpServletRequest wrapped =
                            (HttpServletRequest) servletRequest;
                    observed.set(wrapped.getHeader(RequestId.HEADER_NAME));
                    assertEquals(
                            observed.get(),
                            wrapped.getAttribute(
                                    RequestId.REQUEST_ATTRIBUTE));
                    assertEquals(
                            observed.get(),
                            MDC.get(RequestIdContext.MDC_KEY));
                });

        assertEquals(
                observed.get(),
                response.getHeader(RequestId.HEADER_NAME));
        assertNull(MDC.get(RequestIdContext.MDC_KEY));
        return observed.get();
    }

    private JsonNode readFixture() throws Exception {
        try (InputStream input =
                     getClass().getResourceAsStream(FIXTURE)) {
            assertNotNull(input);
            return OBJECT_MAPPER.readTree(input);
        }
    }
}
