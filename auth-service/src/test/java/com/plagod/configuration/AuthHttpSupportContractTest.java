package com.plagod.configuration;

import com.plagod.controller.VerificationController;
import com.plagod.dto.ApiResponse;
import com.plagod.exception.RefreshSessionException;
import com.plagod.exception.VerificationCodeRateLimitException;
import com.plagod.exception.VerificationDeliveryException;
import com.plagod.request.RequestId;
import com.plagod.service.VerificationCodeService;
import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.RequestIdContext;
import com.plagod.web.RequestIdFilter;
import com.plagod.web.ServletApiExceptionHandlerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthHttpSupportContractTest {

    private static final String REQUEST_ID = "auth-request-id-0001";

    @AfterEach
    void clearRequestId() {
        MDC.remove(RequestIdContext.MDC_KEY);
    }

    @Test
    void usesOneLocalAdviceBuiltOnSharedServletSupport() {
        assertEquals(
                ServletApiExceptionHandlerSupport.class,
                GlobalExceptionHandler.class.getSuperclass());
        assertTrue(GlobalExceptionHandler.class.isAnnotationPresent(
                RestControllerAdvice.class));
        assertFalse(ServletApiExceptionHandlerSupport.class.isAnnotationPresent(
                RestControllerAdvice.class));
    }

    @Test
    void refreshSessionCodeMovesFromDataToErrorKey() {
        MDC.put(RequestIdContext.MDC_KEY, REQUEST_ID);
        GlobalExceptionHandler handler =
                new GlobalExceptionHandler(new ApiErrorResponseFactory());

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleRefreshSessionException(
                        new RefreshSessionException(
                                403,
                                "REFRESH_STEP_UP_REQUIRED",
                                "需要验证码复核"));

        assertEquals(403, response.getStatusCodeValue());
        assertEquals(REQUEST_ID,
                response.getHeaders().getFirst(RequestId.HEADER_NAME));
        assertEquals(403, response.getBody().getCode());
        assertEquals("REFRESH_STEP_UP_REQUIRED",
                response.getBody().getErrorKey());
        assertEquals(REQUEST_ID, response.getBody().getRequestId());
        assertNull(response.getBody().getData());
    }

    @Test
    void verificationRateLimitUsesSharedEnvelopeAndRetryAfter()
            throws Exception {
        VerificationCodeService service = mock(VerificationCodeService.class);
        doThrow(new VerificationCodeRateLimitException("请求过于频繁", 7L))
                .when(service)
                .sendCode(anyString(), anyString(), anyString());

        verificationMockMvc(service)
                .perform(post("/auth/codes")
                        .contentType("application/json")
                        .content("{\"target\":\"13800000000\","
                                + "\"scene\":\"login\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(header().exists(RequestId.HEADER_NAME))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.errorKey").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void verificationProviderFailureDoesNotExposeProviderMessage()
            throws Exception {
        VerificationCodeService service = mock(VerificationCodeService.class);
        doThrow(new VerificationDeliveryException(
                "验证码发送服务暂时不可用",
                new IllegalStateException("provider-secret-canary")))
                .when(service)
                .sendCode(anyString(), anyString(), anyString());

        verificationMockMvc(service)
                .perform(post("/auth/codes")
                        .contentType("application/json")
                        .content("{\"target\":\"13800000000\","
                                + "\"scene\":\"login\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.errorKey")
                        .value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(
                        not(containsString("provider-secret-canary"))));
    }

    private MockMvc verificationMockMvc(
            VerificationCodeService service) {
        GlobalExceptionHandler handler =
                new GlobalExceptionHandler(new ApiErrorResponseFactory());
        return MockMvcBuilders
                .standaloneSetup(new VerificationController(service))
                .setControllerAdvice(handler)
                .addFilters(new RequestIdFilter())
                .build();
    }
}
