package com.plagod.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class InternalWebClientConfiguration {

    @Bean(destroyMethod = "dispose")
    public ConnectionProvider internalConnectionProvider(
            @Value("${wifi.internal-http.max-idle-millis:5000}") long maxIdleMillis,
            @Value("${wifi.internal-http.max-life-millis:300000}") long maxLifeMillis,
            @Value("${wifi.internal-http.eviction-interval-millis:5000}") long evictionIntervalMillis) {
        return ConnectionProvider.builder("gateway-internal")
                .maxIdleTime(Duration.ofMillis(Math.max(1000L, maxIdleMillis)))
                .maxLifeTime(Duration.ofMillis(Math.max(10000L, maxLifeMillis)))
                .evictInBackground(Duration.ofMillis(Math.max(1000L, evictionIntervalMillis)))
                .build();
    }

    @Bean
    @LoadBalanced
    public WebClient.Builder internalWebClientBuilder(
            ConnectionProvider internalConnectionProvider) {
        HttpClient httpClient = HttpClient.create(internalConnectionProvider);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
