package com.plagod.ai.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AiProviderRegistry {

    private static final Pattern PROVIDER_CODE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_-]{0,63}$");

    private final String selectedProviderCode;
    private final Map<String, AiModerationProvider> providers;

    public AiProviderRegistry(
            String selectedProviderCode,
            Collection<AiModerationProvider> providerList) {
        this.selectedProviderCode = normalizeOptional(selectedProviderCode);
        Map<String, AiModerationProvider> registered = new LinkedHashMap<>();
        if (providerList != null) {
            for (AiModerationProvider provider : providerList) {
                if (provider == null) {
                    throw new IllegalArgumentException("Provider 列表不能包含 null");
                }
                String providerCode = normalizeRequired(provider.providerCode());
                AiModerationProvider previous = registered.put(providerCode, provider);
                if (previous != null) {
                    throw new IllegalStateException(
                            "AI Provider 重复注册：" + providerCode);
                }
            }
        }
        this.providers = Collections.unmodifiableMap(registered);
    }

    public Optional<AiModerationProvider> selected() {
        if (selectedProviderCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(selectedProviderCode));
    }

    public Optional<String> selectedProviderCode() {
        return Optional.ofNullable(selectedProviderCode);
    }

    public Optional<AiModerationProvider> get(String providerCode) {
        String normalized = normalizeOptional(providerCode);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(normalized));
    }

    private static String normalizeRequired(String providerCode) {
        String normalized = normalizeOptional(providerCode);
        if (normalized == null) {
            throw new IllegalArgumentException("providerCode 不能为空");
        }
        return normalized;
    }

    private static String normalizeOptional(String providerCode) {
        if (providerCode == null || providerCode.trim().isEmpty()) {
            return null;
        }
        String normalized = providerCode.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDER_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("providerCode 格式非法");
        }
        return normalized;
    }
}
