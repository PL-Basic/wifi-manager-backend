package com.plagod.service.oauth;

import com.plagod.constant.OAuthProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class OAuthProviderRegistry {

    private final Map<OAuthProvider, OAuthProviderAdapter> adapters = new EnumMap<>(OAuthProvider.class);

    public OAuthProviderRegistry(List<OAuthProviderAdapter> providerAdapters) {

        for (OAuthProviderAdapter adapter : providerAdapters) {
            OAuthProviderAdapter previous = adapters.put(adapter.provider(), adapter);

            if (previous != null) {
                throw new IllegalStateException("OAuth Provider 适配器重复：" + adapter.provider().value());
            }
        }
    }

    public OAuthProviderAdapter require(String providerValue) {
        OAuthProvider provider = OAuthProvider.parse(providerValue);

        OAuthProviderAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new IllegalStateException("OAuth Provider 未注册：" + provider.value());
        }
        if (!adapter.isAvailable()) {
            throw new IllegalStateException(provider.value() + " OAuth 当前未配置");
        }

        return adapter;
    }
}