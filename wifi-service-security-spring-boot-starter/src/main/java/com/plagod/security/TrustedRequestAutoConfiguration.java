package com.plagod.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "wifi.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TrustedRequestProperties.class)
public class TrustedRequestAutoConfiguration {

    @Bean
    public FilterRegistrationBean<TrustedRequestFilter>
    trustedRequestFilterRegistration(TrustedRequestProperties properties) {

        FilterRegistrationBean<TrustedRequestFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new TrustedRequestFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setName("trustedRequestFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);

        return registration;
    }
}