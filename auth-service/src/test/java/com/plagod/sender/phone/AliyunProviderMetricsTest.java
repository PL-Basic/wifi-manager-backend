package com.plagod.sender.phone;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.plagod.configuration.PhoneVerificationProperties;
import com.plagod.web.LowCardinalityTagPolicy;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AliyunProviderMetricsTest {

    @Test
    void recordsSuccessfulSendWithOnlyFixedLowCardinalityTags()
            throws Exception {
        String outId = "out-id-canary";
        String phone = "13800000000";
        Client client = successfulSendClient(outId);
        SimpleMeterRegistry registry = meterRegistry();
        AliyunNumberAuthVerificationProvider provider =
                new TestableProvider(
                        configuredProperties(),
                        registry,
                        client);

        PhoneVerificationSendResult result =
                provider.send(phone, "login", outId);

        assertTrue(result.isSuccessful());
        Timer timer = registry.find(
                        AliyunNumberAuthVerificationProvider
                                .PROVIDER_LATENCY_METRIC)
                .tags(
                        "event", "send",
                        "result", "SUCCESS",
                        "type", "ACCEPTED")
                .timer();
        assertNotNull(timer);
        assertEquals(1L, timer.count());
        Set<String> tagKeys = timer.getId().getTags().stream()
                .map(Tag::getKey)
                .collect(Collectors.toSet());
        assertEquals(
                new HashSet<>(Arrays.asList(
                        "event",
                        "result",
                        "type")),
                tagKeys);
        assertFalse(timer.getId().toString().contains(phone));
        assertFalse(timer.getId().toString().contains(outId));
    }

    @Test
    void recordsVerifyConfigurationFailureAsBoundedType() {
        SimpleMeterRegistry registry = meterRegistry();
        AliyunNumberAuthVerificationProvider provider =
                new AliyunNumberAuthVerificationProvider(
                        new PhoneVerificationProperties(
                                new MockEnvironment()),
                        registry);

        PhoneVerificationCheckResult result = provider.verify(
                "13800000000",
                "out-id-canary",
                "123456",
                null);

        assertFalse(result.isRequestSuccessful());
        Timer timer = registry.find(
                        AliyunNumberAuthVerificationProvider
                                .PROVIDER_LATENCY_METRIC)
                .tags(
                        "event", "verify",
                        "result", "FAILURE",
                        "type", "CONFIGURATION")
                .timer();
        assertNotNull(timer);
        assertEquals(1L, timer.count());
    }

    private SimpleMeterRegistry meterRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(
                new LowCardinalityTagPolicy());
        return registry;
    }

    private PhoneVerificationProperties configuredProperties() {
        PhoneVerificationProperties properties =
                new PhoneVerificationProperties(
                        new MockEnvironment());
        properties.setProvider("aliyun-number-auth");
        properties.getAliyun().setAccessKeyId(
                "provider-access-key-id");
        properties.getAliyun().setAccessKeySecret(
                "provider-access-key-secret");
        properties.getAliyun().setSignName("test-sign");
        properties.getAliyun().setTemplateCode("test-template");
        return properties;
    }

    private Client successfulSendClient(String outId)
            throws Exception {
        Client client = mock(Client.class);
        SendSmsVerifyCodeResponse response =
                mock(SendSmsVerifyCodeResponse.class);
        SendSmsVerifyCodeResponseBody body =
                mock(SendSmsVerifyCodeResponseBody.class);
        SendSmsVerifyCodeResponseBody
                .SendSmsVerifyCodeResponseBodyModel model =
                mock(SendSmsVerifyCodeResponseBody
                        .SendSmsVerifyCodeResponseBodyModel.class);
        when(response.getBody()).thenReturn(body);
        when(body.getSuccess()).thenReturn(true);
        when(body.getCode()).thenReturn("OK");
        when(body.getModel()).thenReturn(model);
        when(model.getOutId()).thenReturn(outId);
        when(client.sendSmsVerifyCodeWithOptions(
                any(),
                any())).thenReturn(response);
        return client;
    }

    private static final class TestableProvider
            extends AliyunNumberAuthVerificationProvider {

        private final Client client;

        private TestableProvider(
                PhoneVerificationProperties properties,
                SimpleMeterRegistry registry,
                Client client) {
            super(properties, registry);
            this.client = client;
        }

        @Override
        Client createClient(
                PhoneVerificationProperties.Aliyun config) {
            return client;
        }
    }
}
