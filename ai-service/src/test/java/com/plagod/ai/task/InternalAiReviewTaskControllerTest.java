package com.plagod.ai.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.plagod.AiApplication;
import com.plagod.entity.AiReviewTask;
import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import com.plagod.security.TrustedRequestHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = AiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:ai-controller;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "debug=false",
                "logging.level.org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver=INFO",
                "wifi.internal.token=ai-controller-internal-token",
                "wifi.security.gateway-token=ai-controller-gateway-token",
                "wifi.security.internal-token=ai-controller-internal-token",
                "wifi.security.internal-token-required=true",
                "wifi.ai.task.scan-initial-delay-ms=3600000"
        })
@AutoConfigureMockMvc
class InternalAiReviewTaskControllerTest {

    private static final String PATH = "/internal/ai/review-tasks";
    private static final String INTERNAL_TOKEN =
            "ai-controller-internal-token";
    private static final String REQUEST = "{"
            + "\"reviewRequestId\":\"review-support-001\","
            + "\"scene\":\"SUPPORT_SUBMISSION_REVIEW\","
            + "\"businessType\":\"SUPPORT_SUBMISSION\","
            + "\"businessId\":41,"
            + "\"tenantId\":11,"
            + "\"contentVersion\":2,"
            + "\"contentHash\":\""
            + "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef\","
            + "\"policyVersionId\":999"
            + "}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiReviewTaskEntryService entryService;

    @Test
    void internalEndpointRejectsMissingAndGatewayCredentials()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(PATH)
                        .header(
                                TrustedRequestHeaders.GATEWAY_TOKEN,
                                "ai-controller-gateway-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalCredentialCreatesSupportAlignedTaskWithoutCallerPolicy()
            throws Exception {
        AiReviewTask task = new AiReviewTask();
        task.setReviewTaskId(501L);
        task.setPolicyVersionId(7L);
        when(entryService.submit(any()))
                .thenReturn(new AiReviewTaskEntryResult(task, false));

        mockMvc.perform(post(PATH)
                        .header(
                                TrustedRequestHeaders.INTERNAL_TOKEN,
                                INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reviewTaskId").value(501))
                .andExpect(jsonPath("$.data.policyVersionId").doesNotExist())
                .andExpect(jsonPath("$.data.duplicate").doesNotExist());

        ArgumentCaptor<AiReviewTaskSubmission> submission =
                ArgumentCaptor.forClass(AiReviewTaskSubmission.class);
        verify(entryService).submit(submission.capture());
        assertEquals(
                "review-support-001",
                submission.getValue().getReviewRequestId());
        assertEquals(
                "SUPPORT_SUBMISSION_REVIEW",
                submission.getValue().getScene().name());
        assertEquals(
                "SUPPORT_SUBMISSION",
                submission.getValue().getBusinessType());
        assertEquals(41L, submission.getValue().getBusinessId());
        assertEquals(11L, submission.getValue().getTenantId());
        assertEquals(2, submission.getValue().getContentVersion());
        assertFalse(java.util.Arrays.stream(
                        AiReviewTaskSubmission.class.getDeclaredMethods())
                .anyMatch(method -> "getPolicyVersionId".equals(
                        method.getName())));
    }

    @Test
    void differentFingerprintReturnsConflictEnvelopeWithRequestId()
            throws Exception {
        String requestId = "ai-conflict-req-0001";
        AiReviewTask task = new AiReviewTask();
        task.setReviewTaskId(501L);
        when(entryService.submit(any()))
                .thenReturn(new AiReviewTaskEntryResult(task, false))
                .thenThrow(ApiStatusException.idempotencyConflict(
                        "reviewRequestId 已用于不同的 AI 审核任务"));

        mockMvc.perform(post(PATH)
                        .header(
                                TrustedRequestHeaders.INTERNAL_TOKEN,
                                INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk());

        String conflictingRequest = REQUEST.replace(
                "0123456789abcdef0123456789abcdef"
                        + "0123456789abcdef0123456789abcdef",
                "abcdef0123456789abcdef0123456789"
                        + "abcdef0123456789abcdef0123456789");

        mockMvc.perform(post(PATH)
                        .header(
                                TrustedRequestHeaders.INTERNAL_TOKEN,
                                INTERNAL_TOKEN)
                        .header(RequestId.HEADER_NAME, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingRequest))
                .andExpect(status().isConflict())
                .andExpect(header().string(
                        RequestId.HEADER_NAME,
                        requestId))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.errorKey").value(
                        "IDEMPOTENCY_KEY_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void invalidRequestReturnsStableSafeBadRequestEnvelope()
            throws Exception {
        String requestId = "ai-invalid-req-00001";
        String invalidRequest = REQUEST.replace(
                "SUPPORT_SUBMISSION_REVIEW",
                "UNSUPPORTED_SCENE_CANARY");

        String response = mockMvc.perform(post(PATH)
                        .header(
                                TrustedRequestHeaders.INTERNAL_TOKEN,
                                INTERNAL_TOKEN)
                        .header(RequestId.HEADER_NAME, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        RequestId.HEADER_NAME,
                        requestId))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(
                        "请求参数无效"))
                .andExpect(jsonPath("$.errorKey").value(
                        "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains("UNSUPPORTED_SCENE_CANARY"));
    }

    @Test
    void unexpectedFailureReturnsSafeEnvelopeAndSafeLog()
            throws Exception {
        String requestId = "ai-failure-req-00001";
        String canary = "Bearer AI_THROWABLE_SECRET_CANARY";
        when(entryService.submit(any())).thenThrow(
                new IllegalStateException(canary));

        Logger logger = (Logger) LoggerFactory.getLogger(
                Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String response;
        try {
            response = mockMvc.perform(post(PATH)
                            .header(
                                    TrustedRequestHeaders.INTERNAL_TOKEN,
                                    INTERNAL_TOKEN)
                            .header(RequestId.HEADER_NAME, requestId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().string(
                            RequestId.HEADER_NAME,
                            requestId))
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value(
                            "请求处理失败"))
                    .andExpect(jsonPath("$.errorKey").value(
                            "INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertFalse(response.contains("AI_THROWABLE_SECRET_CANARY"));
        assertFalse(appender.list.isEmpty());
        assertFalse(appender.list.stream()
                .anyMatch(event -> containsCanary(event, canary)));
        String rendered = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(rendered.contains(
                IllegalStateException.class.getName()));
        assertTrue(rendered.contains(requestId));
    }

    private boolean containsCanary(
            ILoggingEvent event,
            String canary) {
        if (event.getFormattedMessage().contains(canary)) {
            return true;
        }
        IThrowableProxy throwable = event.getThrowableProxy();
        while (throwable != null) {
            if (throwable.getMessage() != null
                    && throwable.getMessage().contains(canary)) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }
}
