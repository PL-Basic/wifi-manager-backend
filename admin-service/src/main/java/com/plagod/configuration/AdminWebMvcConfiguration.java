package com.plagod.configuration;

import com.plagod.security.TrustedRequestContextResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebMvcConfiguration implements WebMvcConfigurer {

    private final TrustedRequestContextResolver contextResolver;

    public AdminWebMvcConfiguration(
            TrustedRequestContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
                new AdminRequestScopeInterceptor(contextResolver))
                .addPathPatterns("/admin/**");
    }
}
