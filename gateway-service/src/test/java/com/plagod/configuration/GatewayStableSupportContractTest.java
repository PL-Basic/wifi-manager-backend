package com.plagod.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayStableSupportContractTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(this::loadGatewayConfiguration);

    @Test
    void actuatorExposureAndHealthGroupsRemainRestricted() {
        contextRunner.run(context -> {
            Environment environment = context.getEnvironment();
            assertEquals(
                    Arrays.asList("health", "info"),
                    bindList(
                            environment,
                            "management.endpoints.web.exposure.include"));
            assertEquals(
                    Collections.singletonList("livenessState"),
                    bindList(
                            environment,
                            "management.endpoint.health.group."
                                    + "liveness.include"));
            assertEquals(
                    Arrays.asList(
                            "readinessState",
                            "gatewayReadiness"),
                    bindList(
                            environment,
                            "management.endpoint.health.group."
                                    + "readiness.include"));
            assertEquals(
                    "never",
                    environment.getProperty(
                            "management.endpoint.health.show-details"));
            assertEquals(
                    Boolean.TRUE,
                    environment.getProperty(
                            "management.endpoint.health.probes.enabled",
                            Boolean.class));
            assertEquals(
                    Integer.valueOf(18080),
                    environment.getProperty(
                            "management.server.port",
                            Integer.class));
            assertEquals(
                    "127.0.0.1",
                    environment.getProperty(
                            "management.server.address"));
            assertFalse(
                    environment.getProperty(
                            "management.health.redis.enabled",
                            Boolean.class,
                            true));
        });
    }

    @Test
    void requestIdIsAllowedAndExposedWithGatewayMetricsEnabled() {
        contextRunner.run(context -> {
            Environment environment = context.getEnvironment();
            String corsPrefix = "spring.cloud.gateway.globalcors."
                    + "cors-configurations.[/**].";
            List<String> allowedHeaders = bindList(
                    environment,
                    corsPrefix + "allowed-headers");
            List<String> exposedHeaders = bindList(
                    environment,
                    corsPrefix + "exposed-headers");

            assertTrue(allowedHeaders.contains("X-Request-Id"));
            assertTrue(exposedHeaders.contains("X-Request-Id"));
            assertTrue(
                    environment.getProperty(
                            "spring.cloud.gateway.metrics.enabled",
                            Boolean.class,
                            false));
            assertFalse(
                    environment.getProperty(
                            "spring.cloud.gateway.metrics.tags.path.enabled",
                            Boolean.class,
                            true));
        });
    }

    private List<String> bindList(
            Environment environment,
            String propertyName) {
        return Binder.get(environment)
                .bind(propertyName, Bindable.listOf(String.class))
                .orElse(Collections.emptyList());
    }

    private void loadGatewayConfiguration(
            ConfigurableApplicationContext context) {
        try {
            List<PropertySource<?>> propertySources =
                    new YamlPropertySourceLoader().load(
                            "gatewayApplication",
                            new ClassPathResource("application.yml"));
            for (int index = propertySources.size() - 1;
                 index >= 0;
                 index--) {
                context.getEnvironment().getPropertySources()
                        .addFirst(propertySources.get(index));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法加载 gateway-service application.yml",
                    exception);
        }
    }
}
