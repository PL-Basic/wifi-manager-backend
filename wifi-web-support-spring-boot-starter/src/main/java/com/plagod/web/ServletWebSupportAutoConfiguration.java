package com.plagod.web;

import com.plagod.security.TrustedRequestAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@AutoConfigureAfter(TrustedRequestAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletWebSupportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiErrorResponseFactory apiErrorResponseFactory() {
        return new ApiErrorResponseFactory();
    }

    @Bean
    @ConditionalOnMissingBean(name = "requestIdFilterRegistration")
    public FilterRegistrationBean<RequestIdFilter>
    requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>();
        registration.setFilter(new RequestIdFilter());
        registration.addUrlPatterns("/*");
        registration.setName("requestIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 25);
        return registration;
    }
}
