package com.plagod.ai.provider;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public final class AiHttpClientFactory {

    private AiHttpClientFactory() {
    }

    public static RestTemplate create(HttpAiProviderSettings settings) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(settings.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(settings.getReadTimeoutMillis());
        return new RestTemplate(requestFactory);
    }
}
