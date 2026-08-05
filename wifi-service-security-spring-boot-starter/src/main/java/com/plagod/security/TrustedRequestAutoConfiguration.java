package com.plagod.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import feign.RequestInterceptor;

@Configuration
@AutoConfigureAfter(FeignAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TrustedRequestProperties.class)
public class TrustedRequestAutoConfiguration {

    @Bean
    @Profile("prod")
    public InitializingBean productionTrustedRequestConfigurationValidator(
            TrustedRequestProperties properties,
            @Value("${wifi.internal.token}") String outboundInternalToken) {
        return () -> properties.validateProduction(outboundInternalToken);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wifi.security", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TrustedRequestFilter>
    trustedRequestFilterRegistration(TrustedRequestProperties properties) {

        FilterRegistrationBean<TrustedRequestFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new TrustedRequestFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setName("trustedRequestFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);

        return registration;
    }

    @Bean
    @ConditionalOnClass({RequestInterceptor.class, FeignClientBuilder.class})
    public RequestInterceptor trustedFeignRequestInterceptor(
            @Value("${wifi.internal.token}") String outboundInternalToken) {
        return new TrustedFeignRequestInterceptor(outboundInternalToken);
    }

    @Bean
    @ConditionalOnClass(FeignClientBuilder.class)
    @ConditionalOnMissingBean
    public TenantContextValidationClient tenantContextValidationClient(
            ApplicationContext applicationContext) {
        return new FeignClientBuilder(applicationContext)
                .forType(TenantContextValidationClient.class, "tenant-service")
                .contextId("trustedTenantContextValidationClient")
                .build();
    }

    @Bean
    @ConditionalOnClass(FeignClientBuilder.class)
    @ConditionalOnProperty(prefix = "wifi.security", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TenantContextWriteValidationFilter>
    tenantContextWriteValidationFilterRegistration(
            TenantContextValidationClient validationClient,
            @Value("${spring.application.name:}") String applicationName) {

        FilterRegistrationBean<TenantContextWriteValidationFilter> registration =
                new FilterRegistrationBean<>();
        registration.setFilter(
                new TenantContextWriteValidationFilter(validationClient, applicationName));
        registration.addUrlPatterns("/*");
        registration.setName("tenantContextWriteValidationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return registration;
    }
}
