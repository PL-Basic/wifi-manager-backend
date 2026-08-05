package com.plagod.configuration;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Configuration
public class InternalFeignConfiguration {

    @Value("${wifi.internal.token}")
    private String internalToken;

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(internalToken)
                || internalToken.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("tenant-service 内部调用 Token 必须配置且不能少于16字节");
        }
    }

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return template -> template.header("X-Internal-Token", internalToken);
    }
}
