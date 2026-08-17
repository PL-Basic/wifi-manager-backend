package com.plagod.support;

import com.plagod.SupportApplication;
import com.plagod.dto.ApiResponse;
import com.plagod.mapper.SupportContentReviewOutboxMapper;
import com.plagod.mapper.SupportSubmissionMapper;
import com.plagod.support.client.AiReviewTaskClient;
import com.plagod.support.client.AiReviewTaskRequest;
import com.plagod.support.client.AiReviewTaskResponse;
import feign.RequestInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        classes = SupportApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:support-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "wifi.internal.token=context-test-internal-token",
                "wifi.security.gateway-token=context-test-gateway-token",
                "wifi.security.internal-token=context-test-internal-token",
                "wifi.security.internal-token-required=true",
                "wifi.support.review.scan-initial-delay-ms=3600000"
        })
class SupportApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @MockBean
    private AiReviewTaskClient reviewTaskClient;

    @Test
    void assemblesProductionReviewChainAndRegistersScheduledWorker()
            throws NoSuchMethodException {
        assertNotNull(context.getBean(SupportApplication.class));
        assertNotNull(context.getBean(SupportContentReviewOutboxMapper.class));
        assertNotNull(context.getBean(SupportSubmissionMapper.class));
        assertNotNull(context.getBean(SupportReviewSchedulingConfiguration.class));
        assertNotNull(context.getBean(SupportReviewRemoteGateway.class));
        assertNotNull(context.getBean(SupportReviewDispatcher.class));
        assertNotNull(context.getBean(
                SupportReviewOutboxTransactionService.class));
        assertNotNull(context.getBean(SupportReviewWorker.class));
        assertEquals(
                StableUnits.ASIA_SHANGHAI,
                context.getBean(Clock.class).getZone());

        Map<String, SupportReviewPort> ports =
                context.getBeansOfType(SupportReviewPort.class);
        assertEquals(1, ports.size());
        assertTrue(ports.values().iterator().next()
                instanceof FeignSupportReviewPort);

        FeignClient feignClient =
                AiReviewTaskClient.class.getAnnotation(FeignClient.class);
        assertNotNull(feignClient);
        assertEquals("ai-service", feignClient.name());
        PostMapping createTaskMapping = AiReviewTaskClient.class
                .getMethod(
                        "createReviewTask",
                        AiReviewTaskRequest.class)
                .getAnnotation(PostMapping.class);
        assertNotNull(createTaskMapping);
        assertEquals(
                "/internal/ai/review-tasks",
                createTaskMapping.value()[0]);
        assertNotNull(context.getBean(
                "trustedFeignRequestInterceptor",
                RequestInterceptor.class));

        ScheduledAnnotationBeanPostProcessor scheduling =
                context.getBean(ScheduledAnnotationBeanPostProcessor.class);
        assertFalse(scheduling.getScheduledTasks().isEmpty());
        assertTrue(scheduling.getScheduledTasks().stream()
                .map(Object::toString)
                .anyMatch(task -> task.contains(
                        "SupportReviewWorker.dispatchDueReviews")));
    }

    @Test
    void productionPortUsesFeignTransportWithoutSendingContentBody() {
        when(reviewTaskClient.createReviewTask(any()))
                .thenReturn(ApiResponse.success(
                        new AiReviewTaskResponse(501L)));

        SupportReviewPort port = context.getBean(SupportReviewPort.class);
        SupportReviewReceipt receipt = port.submit(reviewRequest());

        assertEquals(501L, receipt.getReviewTaskId());
        ArgumentCaptor<AiReviewTaskRequest> requestCaptor =
                ArgumentCaptor.forClass(AiReviewTaskRequest.class);
        verify(reviewTaskClient).createReviewTask(requestCaptor.capture());
        AiReviewTaskRequest request = requestCaptor.getValue();
        assertEquals("review-request-01", request.getReviewRequestId());
        assertEquals("SUPPORT_SUBMISSION_REVIEW", request.getScene());
        assertEquals("SUPPORT_SUBMISSION", request.getBusinessType());
        assertEquals(41L, request.getBusinessId());
        assertEquals(11L, request.getTenantId());
        assertEquals(2, request.getContentVersion());
        assertEquals(
                "0123456789abcdef0123456789abcdef"
                        + "0123456789abcdef0123456789abcdef",
                request.getContentHash());
    }

    private SupportReviewRequest reviewRequest() {
        return new SupportReviewRequest(
                "review-request-01",
                "SUPPORT_SUBMISSION_REVIEW",
                "SUPPORT_SUBMISSION",
                41L,
                11L,
                2,
                "0123456789abcdef0123456789abcdef"
                        + "0123456789abcdef0123456789abcdef",
                "不得发送的标题",
                "不得发送的正文");
    }
}
