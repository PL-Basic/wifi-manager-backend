package com.plagod.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为服务提供可被显式配置覆盖的 Actuator 安全默认值。
 */
public final class SafeActuatorEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME =
            "wifiWebSupportSafeActuatorDefaults";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(
                "management.endpoints.web.exposure.include",
                "health,info");
        defaults.put(
                "management.endpoint.health.show-details",
                "never");
        defaults.put(
                "management.endpoint.health.probes.enabled",
                "true");
        defaults.put(
                "management.endpoint.health.group.liveness.include",
                "livenessState");
        defaults.put(
                "management.endpoint.health.group.readiness.include",
                "readinessState");
        environment.getPropertySources().addLast(
                new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
