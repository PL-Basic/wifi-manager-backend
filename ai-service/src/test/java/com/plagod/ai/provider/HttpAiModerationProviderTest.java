package com.plagod.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.ai.model.AiModerationDecision;
import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiModerationScene;
import com.plagod.ai.model.AiProviderAvailability;
import com.plagod.ai.support.AiContentSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiModerationProviderTest {

    private static final String API_KEY = "canary-provider-api-key";

    @Test
    void callsConfiguredHttpsProviderAndParsesFrozenSchema() {
        HttpAiProviderSettings settings = availableSettings();
        RestTemplate restTemplate = AiHttpClientFactory.create(settings);
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(settings.getEndpoint()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + API_KEY))
                .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));
        HttpAiModerationProvider provider = new HttpAiModerationProvider(
                settings,
                restTemplate,
                new ObjectMapper(),
                new AiModerationResultParser(
                        new ObjectMapper(),
                        settings.getMaxResponseBytes()));

        AiModerationResult result = provider.review(request());

        assertEquals(AiModerationDecision.APPROVE, result.getDecision());
        assertEquals(AiProviderAvailability.AVAILABLE, provider.availability());
        server.verify();
    }

    @Test
    void remainsUnavailableUntilPrivacyAndRetentionAreVerified() {
        HttpAiProviderSettings settings = new HttpAiProviderSettings(
                true,
                "provider-a",
                URI.create("https://provider.invalid/review"),
                "model-v1",
                API_KEY,
                3000,
                10000,
                65536,
                false,
                "UNKNOWN");

        assertEquals(
                AiProviderAvailability.PRIVACY_UNVERIFIED,
                settings.availability());
        assertFalse(settings.toString().contains(API_KEY));
    }

    @Test
    void rejectsUnsafeEndpointMetadataAndDoesNotRenderRetentionValue() {
        String retentionCanary = "canary-retention-secret";
        HttpAiProviderSettings settings = new HttpAiProviderSettings(
                true,
                "provider-a",
                URI.create("https://user:password@provider.invalid/review?key=value"),
                "model-v1",
                API_KEY,
                3000,
                10000,
                65536,
                false,
                retentionCanary);

        assertEquals(
                AiProviderAvailability.UNCONFIGURED,
                settings.availability());
        assertFalse(settings.toString().contains(retentionCanary));
        assertFalse(settings.toString().contains("password"));
    }

    @Test
    void mapsTransportTimeoutToFixedFailureWithoutProviderMessage() {
        HttpAiProviderSettings settings = availableSettings();
        RestTemplate timeoutClient = AiHttpClientFactory.create(settings);
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(timeoutClient).build();
        server.expect(requestTo(settings.getEndpoint()))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "provider-body-canary",
                            new SocketTimeoutException("secret-timeout-message"));
                });
        HttpAiModerationProvider provider = new HttpAiModerationProvider(
                settings,
                timeoutClient,
                new ObjectMapper(),
                new AiModerationResultParser(new ObjectMapper(), 65536));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> provider.review(request()));

        assertEquals(AiProviderFailure.TIMEOUT, exception.getFailure());
        assertFalse(exception.getMessage().contains("provider-body-canary"));
        assertFalse(exception.getMessage().contains("secret-timeout-message"));
        assertNull(exception.getCause());
    }

    @Test
    void rejectsOversizeResponseWhileReadingTheResponseStream() {
        HttpAiProviderSettings settings = availableSettings(1024);
        RestTemplate restTemplate = AiHttpClientFactory.create(settings);
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(settings.getEndpoint()))
                .andRespond(withSuccess(
                        repeat('x', 2048),
                        MediaType.APPLICATION_JSON));
        HttpAiModerationProvider provider = new HttpAiModerationProvider(
                settings,
                restTemplate,
                new ObjectMapper(),
                new AiModerationResultParser(new ObjectMapper(), 1024));

        AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> provider.review(request()));

        assertEquals(
                AiProviderFailure.RESPONSE_TOO_LARGE,
                exception.getFailure());
        server.verify();
    }

    @Test
    void rejectsInvalidTimeoutBoundsAtConfigurationBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HttpAiProviderSettings(
                        true,
                        "provider-a",
                        URI.create("https://provider.invalid/review"),
                        "model-v1",
                        API_KEY,
                        0,
                        10000,
                        65536,
                        true,
                        "NO_RETENTION"));
    }

    @Test
    void rejectsExampleProviderSecretsWithoutEchoingTheirValue() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new HttpAiProviderSettings(
                        true,
                        "provider-a",
                        URI.create("https://provider.invalid/review"),
                        "model-v1",
                        "demo-secret",
                        3000,
                        10000,
                        65536,
                        true,
                        "NO_RETENTION"));

        assertFalse(exception.getMessage().contains("demo-secret"));
    }

    private HttpAiProviderSettings availableSettings() {
        return availableSettings(65536);
    }

    private HttpAiProviderSettings availableSettings(int maxResponseBytes) {
        return new HttpAiProviderSettings(
                true,
                "provider-a",
                URI.create("https://provider.invalid/review"),
                "model-v1",
                API_KEY,
                3000,
                10000,
                maxResponseBytes,
                true,
                "NO_RETENTION");
    }

    private AiModerationRequest request() {
        AiContentSecurity security = new AiContentSecurity();
        return new AiModerationRequest(
                AiModerationScene.ANNOUNCEMENT_REVIEW,
                "review-request-1",
                "policy-v1",
                "title",
                "body",
                security.contentHash("title", "body"),
                "zh-CN");
    }

    private String validResponse() {
        return "{"
                + "\"schemaVersion\":\"ai-moderation-result-v1\","
                + "\"decision\":\"APPROVE\","
                + "\"confidence\":0.93,"
                + "\"reasonCode\":\"POLICY_CLEAR\","
                + "\"riskLabels\":[],"
                + "\"providerRequestId\":\"provider-request-1\","
                + "\"model\":\"model-v1\","
                + "\"latencyMillis\":20"
                + "}";
    }

    private String repeat(char value, int count) {
        StringBuilder output = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
