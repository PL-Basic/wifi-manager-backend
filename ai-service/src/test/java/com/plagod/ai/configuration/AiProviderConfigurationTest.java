package com.plagod.ai.configuration;

import com.plagod.ai.health.AiProviderHealthIndicator;
import com.plagod.ai.model.AiModerationDecision;
import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiModerationScene;
import com.plagod.ai.model.AiProviderAvailability;
import com.plagod.ai.provider.HttpAiModerationProvider;
import com.plagod.ai.provider.AiProviderRegistry;
import com.plagod.ai.service.AiModerationService;
import com.plagod.ai.support.AiContentSecurity;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderConfigurationTest {

    private static final String API_KEY =
            "canary-context-provider-api-key";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(
                            MeterRegistry.class,
                            SimpleMeterRegistry::new)
                    .withUserConfiguration(
                            AiProviderConfiguration.class);

    @Test
    void startsWithoutAnOptionalProviderAndReportsUnconfigured() {
        contextRunner.run(context -> {
            AiModerationService service =
                    context.getBean(AiModerationService.class);
            AiProviderRegistry registry =
                    context.getBean(AiProviderRegistry.class);
            assertFalse(registry.selected().isPresent());
            assertEquals(
                    Status.UNKNOWN,
                    context.getBean(AiProviderHealthIndicator.class)
                            .health()
                            .getStatus());

            AiModerationResult result = service.review(request(), 0.80d);
            assertEquals(AiModerationDecision.MANUAL, result.getDecision());
            assertEquals(
                    "AI_PROVIDER_UNAVAILABLE",
                    result.getReasonCode());
        });
    }

    @Test
    void registersAndSelectsHttpProviderWithCompleteSafeConfiguration() {
        contextRunner
                .withPropertyValues(safeHttpConfiguration())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    HttpAiModerationProvider provider =
                            context.getBean(HttpAiModerationProvider.class);
                    AiProviderRegistry registry =
                            context.getBean(AiProviderRegistry.class);

                    assertTrue(registry.selected().isPresent());
                    assertSame(provider, registry.selected().get());
                    assertEquals(
                            AiProviderAvailability.AVAILABLE,
                            provider.availability());
                    assertEquals(
                            Status.UP,
                            context.getBean(AiProviderHealthIndicator.class)
                                    .health()
                                    .getStatus());
                });
    }

    @Test
    void failsFastWhenSelectedProviderIsUnknown() {
        String unknownProvider = "canary-unknown-provider";
        contextRunner
                .withPropertyValues(
                        "wifi.ai.provider.selected=" + unknownProvider)
                .run(context -> assertSafeStartupFailure(
                        context.getStartupFailure(),
                        unknownProvider));
    }

    @Test
    void failsFastWhenSelectedHttpProviderConfigurationIsMissing() {
        contextRunner
                .withPropertyValues(
                        "wifi.ai.provider.selected=http",
                        "wifi.ai.provider.http.enabled=true",
                        "wifi.ai.provider.http.api-key=" + API_KEY)
                .run(context -> assertSafeStartupFailure(
                        context.getStartupFailure(),
                        API_KEY));
    }

    @Test
    void failsFastWhenSelectedHttpProviderPrivacyIsUnverified() {
        String[] properties = safeHttpConfiguration();
        properties[properties.length - 2] =
                "wifi.ai.provider.http.no-training-supported=false";
        contextRunner
                .withPropertyValues(properties)
                .run(context -> assertSafeStartupFailure(
                        context.getStartupFailure(),
                        API_KEY));
    }

    @Test
    void failsFastWhenSelectedHttpProviderRetentionIsUnapproved() {
        String[] properties = safeHttpConfiguration();
        properties[properties.length - 1] =
                "wifi.ai.provider.http.retention-mode=UNKNOWN";
        contextRunner
                .withPropertyValues(properties)
                .run(context -> assertSafeStartupFailure(
                        context.getStartupFailure(),
                        API_KEY));
    }

    @Test
    void rejectsExampleApiKeyWithoutEchoingIt() {
        String exampleKey = "demo-secret";
        String[] properties = safeHttpConfiguration();
        properties[5] =
                "wifi.ai.provider.http.api-key=" + exampleKey;
        contextRunner
                .withPropertyValues(properties)
                .run(context -> assertSafeStartupFailure(
                        context.getStartupFailure(),
                        exampleKey));
    }

    private String[] safeHttpConfiguration() {
        return new String[]{
                "wifi.ai.provider.selected=http",
                "wifi.ai.provider.http.enabled=true",
                "wifi.ai.provider.http.provider-code=http",
                "wifi.ai.provider.http.endpoint=https://provider.invalid/review",
                "wifi.ai.provider.http.model=model-v1",
                "wifi.ai.provider.http.api-key=" + API_KEY,
                "wifi.ai.provider.http.connect-timeout-millis=3000",
                "wifi.ai.provider.http.read-timeout-millis=10000",
                "wifi.ai.provider.http.max-response-bytes=65536",
                "wifi.ai.provider.http.no-training-supported=true",
                "wifi.ai.provider.http.retention-mode=NO_RETENTION"
        };
    }

    private void assertSafeStartupFailure(
            Throwable failure,
            String canary) {
        assertNotNull(failure);
        assertFalse(
                renderFailure(failure).contains(canary),
                "启动异常不得回显配置 canary");
    }

    private String renderFailure(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    private AiModerationRequest request() {
        AiContentSecurity security = new AiContentSecurity();
        return new AiModerationRequest(
                AiModerationScene.ANNOUNCEMENT_REVIEW,
                "review-request-context",
                "policy-v1",
                "title",
                "body",
                security.contentHash("title", "body"),
                "zh-CN");
    }
}
