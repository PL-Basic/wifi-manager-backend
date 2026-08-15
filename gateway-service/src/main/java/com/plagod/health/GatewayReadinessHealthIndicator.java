package com.plagod.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GatewayReadinessHealthIndicator
        implements ReactiveHealthIndicator {

    private static final Set<String> REQUIRED_ROUTE_IDS =
            new HashSet<>(Arrays.asList(
                    "auth-service-route",
                    "user-service-route",
                    "tenant-service-route",
                    "device-user-route",
                    "admin-service-route",
                    "monitor-user-route",
                    "monitor-websocket-route"));

    private static final Duration CHECK_TIMEOUT =
            Duration.ofSeconds(2);

    private final RouteDefinitionLocator routeDefinitionLocator;
    private final DiscoveryClient discoveryClient;

    public GatewayReadinessHealthIndicator(
            RouteDefinitionLocator routeDefinitionLocator,
            DiscoveryClient discoveryClient) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public Mono<Health> health() {
        Mono<Boolean> routesReady = routeDefinitionLocator
                .getRouteDefinitions()
                .map(definition -> definition.getId())
                .collect(Collectors.toSet())
                .map(routeIds -> routeIds.containsAll(REQUIRED_ROUTE_IDS))
                .onErrorReturn(false);

        return Mono.zip(
                        routesReady,
                        hasInstances("auth-service"),
                        hasInstances("tenant-service"))
                .map(result -> result.getT1()
                        && result.getT2()
                        && result.getT3()
                        ? Health.up().build()
                        : Health.down().build())
                .timeout(
                        CHECK_TIMEOUT,
                        Mono.just(Health.down().build()))
                .onErrorReturn(Health.down().build());
    }

    private Mono<Boolean> hasInstances(String serviceId) {
        return Mono.fromCallable(
                        () -> !discoveryClient.getInstances(serviceId)
                                .isEmpty())
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(false);
    }
}
