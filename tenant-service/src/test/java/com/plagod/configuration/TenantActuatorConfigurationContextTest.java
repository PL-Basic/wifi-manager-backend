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

class TenantActuatorConfigurationContextTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(this::loadTenantApplicationConfiguration);

    @Test
    void persistenceReadinessIncludesDatabaseButLivenessDoesNot() {
        contextRunner.run(context -> {
            List<String> readiness = bindList(
                    context.getEnvironment(),
                    "management.endpoint.health.group.readiness.include");
            List<String> liveness = bindList(
                    context.getEnvironment(),
                    "management.endpoint.health.group.liveness.include");

            assertEquals(Arrays.asList("readinessState", "db"), readiness);
            assertEquals(Collections.singletonList("livenessState"), liveness);
            assertFalse(liveness.contains("db"));
        });
    }

    @Test
    void actuatorExposureAndHealthDetailsRemainRestricted() {
        contextRunner.run(context -> {
            List<String> exposedEndpoints = bindList(
                    context.getEnvironment(),
                    "management.endpoints.web.exposure.include");

            assertEquals(Arrays.asList("health", "info"), exposedEndpoints);
            assertEquals(
                    "never",
                    context.getEnvironment().getProperty(
                            "management.endpoint.health.show-details"));
            assertEquals(
                    Boolean.TRUE,
                    context.getEnvironment().getProperty(
                            "management.endpoint.health.probes.enabled",
                            Boolean.class));
        });
    }

    private List<String> bindList(
            Environment environment,
            String propertyName) {
        return Binder.get(environment)
                .bind(propertyName, Bindable.listOf(String.class))
                .orElse(Collections.emptyList());
    }

    private void loadTenantApplicationConfiguration(
            ConfigurableApplicationContext context) {
        try {
            List<PropertySource<?>> propertySources =
                    new YamlPropertySourceLoader().load(
                            "tenantApplication",
                            new ClassPathResource("application.yml"));
            for (int index = propertySources.size() - 1;
                 index >= 0;
                 index--) {
                context.getEnvironment().getPropertySources()
                        .addFirst(propertySources.get(index));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法加载 tenant-service application.yml",
                    exception);
        }
    }
}
