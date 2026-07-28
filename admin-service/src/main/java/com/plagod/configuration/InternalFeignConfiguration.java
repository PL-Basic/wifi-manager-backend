package com.plagod.configuration;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

@Configuration
public class InternalFeignConfiguration {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final String[] TRUSTED_IDENTITY_HEADERS = {
            "X-User-Id",
            "X-User-Name",
            "X-User-Role"
    };

    @Value("${wifi.internal.token}")
    private String internalToken;

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(internalToken) || internalToken.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("admin-service 内部调用 Token 必须配置且不能少于 16 字节");
        }
    }

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return template -> {
            template.header(INTERNAL_TOKEN_HEADER, internalToken);
            HttpServletRequest currentRequest = currentRequest();
            if (currentRequest == null) {
                return;
            }
            // 这些身份 Header 已由 Gateway 清洗并重新注入，可以继续传给下游服务。
            for (String headerName : TRUSTED_IDENTITY_HEADERS) {
                String value = currentRequest.getHeader(headerName);

                if (StringUtils.hasText(value)) {
                    template.header(headerName, value);
                }
            }
        };
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (!(attributes instanceof ServletRequestAttributes)) {
            return null;
        }

        return ((ServletRequestAttributes) attributes).getRequest();
    }
}