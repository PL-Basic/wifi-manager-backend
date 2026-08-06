package com.plagod.configuration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class InternalWebClientConfiguration {

    @Bean
    @LoadBalanced
    public WebClient.Builder internalWebClientBuilder() {
        return WebClient.builder();
    }
}
