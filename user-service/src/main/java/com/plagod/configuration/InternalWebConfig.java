package com.plagod.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class InternalWebConfig implements WebMvcConfigurer {

    @Autowired
    private InternalApiInterceptor internalApiInterceptor;

    // 只保护内部接口，不影响现有 /users/** 接口。
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalApiInterceptor)
                .addPathPatterns("/internal/**");
    }
}