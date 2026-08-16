package com.plagod.configuration;

import com.plagod.health.AuthVerificationProviderHealthIndicator;
import com.plagod.support.PageBounds;
import com.plagod.support.StableUnits;
import com.plagod.web.LowCardinalityTagPolicy;
import com.plagod.web.SafeActuatorEnvironmentPostProcessor;
import com.plagod.web.ServletWebSupportAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthStableSupportContractTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void consumesSharedPageUnitAndLowCardinalityContracts() {
        PageBounds bounds = PageBounds.of(0, 1_000);
        assertEquals(1, bounds.getCurrent());
        assertEquals(100, bounds.getSize());
        assertEquals("Asia/Shanghai",
                StableUnits.ASIA_SHANGHAI.getId());

        new WebApplicationContextRunner()
                .withUserConfiguration(
                        ServletWebSupportAutoConfiguration.class)
                .run(context -> assertEquals(
                        1,
                        context.getBeansOfType(
                                LowCardinalityTagPolicy.class).size()));
    }

    @Test
    void rejectsInvalidVerificationLimitsWithoutEchoingValues() {
        VerificationCodeProperties properties =
                new VerificationCodeProperties();
        properties.setTargetIntervalSeconds(0);

        Set<ConstraintViolation<VerificationCodeProperties>> violations =
                validator.validate(properties);

        assertTrue(violations.stream().anyMatch(
                violation -> "targetIntervalSeconds".equals(
                        violation.getPropertyPath().toString())));
    }

    @Test
    void rejectsInvalidProviderTimeout() {
        PhoneVerificationProperties properties = aliyunProperties();
        properties.getAliyun().setReadTimeoutMillis(30_001);

        Set<ConstraintViolation<PhoneVerificationProperties>> violations =
                validator.validate(properties);

        assertTrue(violations.stream().anyMatch(
                violation -> "aliyun.readTimeoutMillis".equals(
                        violation.getPropertyPath().toString())));
    }

    @Test
    void rejectsUnsafeOptionalSecretWithoutEchoingIt() {
        PhoneVerificationProperties properties =
                aliyunProperties();
        String canary = "canary-short";
        properties.getAliyun().setAccessKeySecret(canary);

        IllegalStateException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalStateException.class,
                        properties::validateSafeConfiguration);

        assertFalse(failure.getMessage().contains(canary));
        assertTrue(failure.getMessage().contains(
                "verification-code.phone.aliyun.access-key-secret"));
    }

    @Test
    void rejectsLocalProviderOnlyInProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        PhoneVerificationProperties prodLocal =
                new PhoneVerificationProperties(prod);
        prodLocal.setProvider(" LOCAL ");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                prodLocal::validateSafeConfiguration);
        assertTrue(failure.getMessage().contains("prod profile"));

        PhoneVerificationProperties demoLocal =
                new PhoneVerificationProperties(new MockEnvironment());
        demoLocal.setProvider("local");
        assertDoesNotThrow(demoLocal::validateSafeConfiguration);

        PhoneVerificationProperties prodAliyun =
                aliyunProperties(prod);
        assertDoesNotThrow(prodAliyun::validateSafeConfiguration);
    }

    @Test
    void phonePropertiesToStringDoesNotExposeProviderSecrets() {
        String environmentCanary = "environment-secret-canary";
        MockEnvironment environment = new MockEnvironment()
                .withProperty("verification.environment.canary",
                        environmentCanary);
        PhoneVerificationProperties properties =
                aliyunProperties(environment);
        String accessKeyIdCanary = "access-key-id-canary";
        String accessKeySecretCanary = "access-key-secret-canary-value";
        properties.getAliyun().setAccessKeyId(accessKeyIdCanary);
        properties.getAliyun().setAccessKeySecret(accessKeySecretCanary);

        String parentText = properties.toString();
        String aliyunText = properties.getAliyun().toString();
        assertFalse(parentText.contains(accessKeyIdCanary));
        assertFalse(parentText.contains(accessKeySecretCanary));
        assertFalse(parentText.contains(environmentCanary));
        assertFalse(parentText.contains("MockEnvironment"));
        assertFalse(aliyunText.contains(accessKeyIdCanary));
        assertFalse(aliyunText.contains(accessKeySecretCanary));
    }

    @Test
    void providerHealthIsSafeAndOptionalProviderDoesNotFailReadiness() {
        PhoneVerificationProperties properties = aliyunProperties();
        properties.setProvider("aliyun-number-auth");
        properties.getAliyun().setAccessKeyId("");
        properties.getAliyun().setAccessKeySecret("");

        Health health =
                new AuthVerificationProviderHealthIndicator(
                        properties).health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals("UNCONFIGURED",
                health.getDetails().get("availability"));
        assertFalse(health.getDetails().toString().contains("secret"));
    }

    @Test
    void authReadinessRequiresDatabaseAndKeepsSharedSafeDefaults()
            throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load(
                        "authApplication",
                        new ClassPathResource("application.yml"));
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addFirst(source);
        }
        new SafeActuatorEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null);

        assertEquals(
                "readinessState,db",
                environment.getProperty(
                        "management.endpoint.health.group."
                                + "readiness.include"));
        assertEquals(
                "livenessState",
                environment.getProperty(
                        "management.endpoint.health.group."
                                + "liveness.include"));
        assertEquals(
                "health,info",
                environment.getProperty(
                        "management.endpoints.web.exposure.include"));
        assertEquals(
                "false",
                environment.getProperty(
                        "management.health.mail.enabled"));
        assertEquals(
                "false",
                environment.getProperty(
                        "management.health.redis.enabled"));
    }

    private PhoneVerificationProperties aliyunProperties() {
        return aliyunProperties(new MockEnvironment());
    }

    private PhoneVerificationProperties aliyunProperties(
            MockEnvironment environment) {
        PhoneVerificationProperties properties =
                new PhoneVerificationProperties(environment);
        properties.setProvider("aliyun-number-auth");
        properties.getAliyun().setAccessKeyId(
                "provider-access-key-id");
        properties.getAliyun().setAccessKeySecret(
                "provider-access-key-secret");
        properties.getAliyun().setSignName("test-sign");
        properties.getAliyun().setTemplateCode("test-template");
        return properties;
    }
}
