package com.plagod.ai.task;

import com.plagod.AiApplication;
import com.plagod.mapper.AiPolicyVersionMapper;
import com.plagod.mapper.AiReviewTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = AiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:ai-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "wifi.internal.token=ai-context-internal-token",
                "wifi.security.gateway-token=ai-context-gateway-token",
                "wifi.security.internal-token=ai-context-internal-token",
                "wifi.security.internal-token-required=true",
                "wifi.ai.task.scan-initial-delay-ms=3600000"
        })
class AiApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void assemblesBootWebPersistenceAndScheduledTaskChain() {
        assertNotNull(context.getBean(AiApplication.class));
        assertNotNull(context.getBean(InternalAiReviewTaskController.class));
        assertNotNull(context.getBean(AiReviewTaskEntryService.class));
        assertNotNull(context.getBean(AiReviewTaskMapper.class));
        assertNotNull(context.getBean(AiPolicyVersionMapper.class));
        assertNotNull(context.getBean(AiReviewTaskWorker.class));
        assertNotNull(context.getBean("trustedRequestFilterRegistration"));

        ScheduledAnnotationBeanPostProcessor scheduling =
                context.getBean(ScheduledAnnotationBeanPostProcessor.class);
        assertFalse(scheduling.getScheduledTasks().isEmpty());
        assertTrue(scheduling.getScheduledTasks().stream()
                .map(Object::toString)
                .anyMatch(task -> task.contains(
                        "AiReviewTaskWorker.dispatchDueTasks")));
    }
}
