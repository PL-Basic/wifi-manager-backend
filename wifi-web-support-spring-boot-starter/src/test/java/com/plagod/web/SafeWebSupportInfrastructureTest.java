package com.plagod.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeWebSupportInfrastructureTest {

    @Test
    void actuatorDefaultsAreLowestPriorityAndExplicitValuesWin() {
        StandardEnvironment defaultsEnvironment =
                new StandardEnvironment();
        SafeActuatorEnvironmentPostProcessor processor =
                new SafeActuatorEnvironmentPostProcessor();
        processor.postProcessEnvironment(defaultsEnvironment, null);

        assertEquals(
                "health,info",
                defaultsEnvironment.getProperty(
                        "management.endpoints.web.exposure.include"));
        assertEquals(
                "never",
                defaultsEnvironment.getProperty(
                        "management.endpoint.health.show-details"));
        assertEquals(
                "true",
                defaultsEnvironment.getProperty(
                        "management.endpoint.health.probes.enabled"));
        assertEquals(
                "livenessState",
                defaultsEnvironment.getProperty(
                        "management.endpoint.health.group.liveness.include"));
        assertEquals(
                "readinessState",
                defaultsEnvironment.getProperty(
                        "management.endpoint.health.group.readiness.include"));
        assertEquals(Ordered.LOWEST_PRECEDENCE, processor.getOrder());
        assertEquals(
                SafeActuatorEnvironmentPostProcessor.PROPERTY_SOURCE_NAME,
                lastPropertySourceName(defaultsEnvironment));

        StandardEnvironment explicitEnvironment =
                new StandardEnvironment();
        explicitEnvironment.getPropertySources().addFirst(
                new MapPropertySource(
                        "serviceConfiguration",
                        Collections.<String, Object>singletonMap(
                                "management.endpoints.web.exposure.include",
                                "health,prometheus")));
        processor.postProcessEnvironment(explicitEnvironment, null);

        assertEquals(
                "health,prometheus",
                explicitEnvironment.getProperty(
                        "management.endpoints.web.exposure.include"));
    }

    @Test
    void springFactoriesKeepsOneAutoConfigurationAndRegistersProcessor()
            throws Exception {
        Properties moduleFactories = moduleSpringFactories();

        assertEquals(
                ServletWebSupportAutoConfiguration.class.getName(),
                moduleFactories.getProperty(
                        EnableAutoConfiguration.class.getName()));
        assertEquals(
                SafeActuatorEnvironmentPostProcessor.class.getName(),
                moduleFactories.getProperty(
                        EnvironmentPostProcessor.class.getName()));
    }

    @Test
    void autoConfigurationRegistersOneOverridableMeterFilter() {
        WebApplicationContextRunner runner =
                new WebApplicationContextRunner()
                        .withConfiguration(AutoConfigurations.of(
                                ServletWebSupportAutoConfiguration.class));

        runner.run(context -> {
            assertEquals(
                    1,
                    context.getBeansOfType(MeterFilter.class).size());
            assertEquals(
                    1,
                    context.getBeansOfType(
                            LowCardinalityTagPolicy.class).size());
            assertNotNull(context.getBean(
                    "wifiLowCardinalityMeterFilter",
                    MeterFilter.class));
        });

        runner.withUserConfiguration(
                        CustomMeterFilterConfiguration.class)
                .run(context -> {
                    assertEquals(
                            1,
                            context.getBeansOfType(
                                    MeterFilter.class).size());
                    assertTrue(context.getBeansOfType(
                            LowCardinalityTagPolicy.class).isEmpty());
                    assertSame(
                            CustomMeterFilterConfiguration.CUSTOM_FILTER,
                            context.getBean(
                                    "wifiLowCardinalityMeterFilter",
                                    MeterFilter.class));
                });
    }

    @Test
    void meterRegistryDeniesInvalidWifiTagsAndKeepsJvmMetersNeutral() {
        LowCardinalityTagPolicy policy =
                new LowCardinalityTagPolicy();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(policy);

        try {
            Counter.builder("wifi.http.requests")
                    .tags(
                            "method", "GET",
                            "status", "200",
                            "outcome", "SUCCESS",
                            "uri", "/devices/{deviceId}")
                    .register(registry);
            assertNotNull(registry.find("wifi.http.requests")
                    .tags(
                            "method", "GET",
                            "status", "200",
                            "outcome", "SUCCESS",
                            "uri", "/devices/{deviceId}")
                    .counter());

            String[] aliases = {
                    "principal",
                    "customer",
                    "node",
                    "owner",
                    "member"
            };
            for (String alias : aliases) {
                assertDenied(
                        registry,
                        "wifi.rejected.alias." + alias,
                        alias,
                        "stable-looking-value");
            }

            assertDenied(
                    registry,
                    "wifi.rejected.uri.numeric",
                    "uri",
                    "/devices/123");
            assertDenied(
                    registry,
                    "wifi.rejected.uri.uuid",
                    "uri",
                    "/devices/a50e8400-e29b-41d4-a716-446655440000");
            assertDenied(
                    registry,
                    "wifi.rejected.uri.mac",
                    "uri",
                    "/devices/AA:BB:CC:DD:EE:FF");
            assertDenied(
                    registry,
                    "wifi.rejected.uri.ip",
                    "uri",
                    "/devices/192.168.1.10");

            Counter.builder("jvm.memory.used")
                    .tag(
                            "principal",
                            "550e8400-e29b-41d4-a716-446655440000")
                    .register(registry);
            assertNotNull(registry.find("jvm.memory.used")
                    .tag(
                            "principal",
                            "550e8400-e29b-41d4-a716-446655440000")
                    .counter());
        } finally {
            registry.close();
        }
    }

    @Test
    void meterRegistryDeniesDistinctValuesAfterBoundWithoutGrowingState() {
        LowCardinalityTagPolicy policy =
                new LowCardinalityTagPolicy(2);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(policy);

        try {
            registry.counter(
                    "wifi.distinct.events",
                    "event",
                    "first");
            registry.counter(
                    "wifi.distinct.events",
                    "event",
                    "second");
            for (int index = 0; index < 20; index++) {
                registry.counter(
                        "wifi.distinct.events",
                        "event",
                        "overflow" + index);
            }

            assertEquals(2, policy.observedValueCount("event"));
            assertNotNull(registry.find("wifi.distinct.events")
                    .tag("event", "first")
                    .counter());
            assertNull(registry.find("wifi.distinct.events")
                    .tag("event", "overflow0")
                    .counter());
        } finally {
            registry.close();
        }
    }

    @Test
    void infrastructureContractUsesBoot23AndJava8CompatibleApis() {
        assertNotNull(SpringBootVersion.getVersion());
        assertTrue(SpringBootVersion.getVersion().startsWith("2.3."));
        assertTrue(EnvironmentPostProcessor.class.isAssignableFrom(
                SafeActuatorEnvironmentPostProcessor.class));
    }

    private Properties moduleSpringFactories() throws Exception {
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources("META-INF/spring.factories");
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            Properties properties = new Properties();
            try (InputStream input = resource.openStream()) {
                properties.load(input);
            }
            if (ServletWebSupportAutoConfiguration.class.getName().equals(
                    properties.getProperty(
                            EnableAutoConfiguration.class.getName()))) {
                return properties;
            }
        }
        throw new AssertionError(
                "module META-INF/spring.factories was not found");
    }

    private void assertDenied(
            SimpleMeterRegistry registry,
            String meterName,
            String key,
            String value) {
        Counter.builder(meterName)
                .tag(key, value)
                .register(registry);
        assertNull(registry.find(meterName)
                .tag(key, value)
                .counter());
    }

    private String lastPropertySourceName(
            StandardEnvironment environment) {
        String last = null;
        for (PropertySource<?> propertySource
                : environment.getPropertySources()) {
            last = propertySource.getName();
        }
        return last;
    }

    @Configuration
    static class CustomMeterFilterConfiguration {

        private static final MeterFilter CUSTOM_FILTER =
                new MeterFilter() {
                };

        @Bean(name = "wifiLowCardinalityMeterFilter")
        MeterFilter customMeterFilter() {
            return CUSTOM_FILTER;
        }
    }
}
