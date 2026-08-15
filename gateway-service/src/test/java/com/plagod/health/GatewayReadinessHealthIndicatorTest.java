package com.plagod.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayReadinessHealthIndicatorTest {

    private static final List<String> REQUIRED_ROUTES = Arrays.asList(
            "auth-service-route",
            "user-service-route",
            "tenant-service-route",
            "device-user-route",
            "admin-service-route",
            "monitor-user-route",
            "monitor-websocket-route");

    @Test
    void readinessIsUpWhenRoutesAndIdentityServicesExist() {
        RouteDefinitionLocator routes = routes(REQUIRED_ROUTES);
        DiscoveryClient discovery = discovery(true, true);
        GatewayReadinessHealthIndicator indicator =
                new GatewayReadinessHealthIndicator(routes, discovery);

        assertEquals(Status.UP, indicator.health().block().getStatus());
    }

    @Test
    void readinessIsDownWhenTenantValidationServiceIsMissing() {
        RouteDefinitionLocator routes = routes(REQUIRED_ROUTES);
        DiscoveryClient discovery = discovery(true, false);
        GatewayReadinessHealthIndicator indicator =
                new GatewayReadinessHealthIndicator(routes, discovery);

        assertEquals(Status.DOWN, indicator.health().block().getStatus());
    }

    @Test
    void readinessIsDownWhenFrozenRouteIsMissing() {
        RouteDefinitionLocator routes = routes(
                REQUIRED_ROUTES.subList(0, REQUIRED_ROUTES.size() - 1));
        DiscoveryClient discovery = discovery(true, true);
        GatewayReadinessHealthIndicator indicator =
                new GatewayReadinessHealthIndicator(routes, discovery);

        assertEquals(Status.DOWN, indicator.health().block().getStatus());
    }

    private RouteDefinitionLocator routes(List<String> routeIds) {
        RouteDefinitionLocator locator = mock(RouteDefinitionLocator.class);
        when(locator.getRouteDefinitions()).thenReturn(
                Flux.fromIterable(routeIds)
                        .map(this::route));
        return locator;
    }

    private RouteDefinition route(String routeId) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(routeId);
        return definition;
    }

    private DiscoveryClient discovery(
            boolean authAvailable,
            boolean tenantAvailable) {
        DiscoveryClient client = mock(DiscoveryClient.class);
        when(client.getInstances("auth-service")).thenReturn(
                authAvailable ? instance("auth-service")
                        : Collections.emptyList());
        when(client.getInstances("tenant-service")).thenReturn(
                tenantAvailable ? instance("tenant-service")
                        : Collections.emptyList());
        return client;
    }

    private List<ServiceInstance> instance(String serviceId) {
        return Collections.singletonList(
                new DefaultServiceInstance(
                        serviceId + "-1",
                        serviceId,
                        "127.0.0.1",
                        8080,
                        false));
    }
}
