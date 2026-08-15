package com.plagod.ai.provider;

import com.plagod.ai.model.AiProviderAvailability;
import com.plagod.support.SafeConfigurationValue;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

public final class HttpAiProviderSettings {

    private static final Pattern PROVIDER_CODE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_-]{0,63}$");

    private final boolean enabled;
    private final String providerCode;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int maxResponseBytes;
    private final boolean noTrainingSupported;
    private final String retentionMode;

    public HttpAiProviderSettings(
            boolean enabled,
            String providerCode,
            URI endpoint,
            String model,
            String apiKey,
            int connectTimeoutMillis,
            int readTimeoutMillis,
            int maxResponseBytes,
            boolean noTrainingSupported,
            String retentionMode) {
        this.enabled = enabled;
        this.providerCode = normalizeProviderCode(providerCode);
        this.endpoint = endpoint;
        this.model = boundedOptional(model, 96);
        this.apiKey = safeOptionalSecret(apiKey);
        this.connectTimeoutMillis = boundedTimeout(
                connectTimeoutMillis,
                "connectTimeoutMillis");
        this.readTimeoutMillis = boundedTimeout(
                readTimeoutMillis,
                "readTimeoutMillis");
        if (maxResponseBytes < 1024 || maxResponseBytes > 262144) {
            throw new IllegalArgumentException("maxResponseBytes 超过允许范围");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.noTrainingSupported = noTrainingSupported;
        this.retentionMode = retentionMode == null
                ? "UNKNOWN"
                : retentionMode.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public String getModel() {
        return model;
    }

    String getApiKey() {
        return apiKey;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public boolean isNoTrainingSupported() {
        return noTrainingSupported;
    }

    public String getRetentionMode() {
        return retentionMode;
    }

    public AiProviderAvailability availability() {
        if (!enabled) {
            return AiProviderAvailability.DISABLED;
        }
        if (providerCode == null
                || !isSafeEndpoint(endpoint)
                || model == null
                || apiKey == null) {
            return AiProviderAvailability.UNCONFIGURED;
        }
        if (!noTrainingSupported || !"NO_RETENTION".equals(retentionMode)) {
            return AiProviderAvailability.PRIVACY_UNVERIFIED;
        }
        return AiProviderAvailability.AVAILABLE;
    }

    @Override
    public String toString() {
        return "HttpAiProviderSettings{"
                + "enabled=" + enabled
                + ", providerCode='" + providerCode + '\''
                + ", endpointConfigured=" + (endpoint != null)
                + ", modelConfigured=" + (model != null)
                + ", apiKeyConfigured=" + (apiKey != null)
                + ", connectTimeoutMillis=" + connectTimeoutMillis
                + ", readTimeoutMillis=" + readTimeoutMillis
                + ", maxResponseBytes=" + maxResponseBytes
                + ", noTrainingSupported=" + noTrainingSupported
                + ", retentionModeApproved="
                + "NO_RETENTION".equals(retentionMode)
                + '}';
    }

    private static boolean isSafeEndpoint(URI value) {
        return value != null
                && "https".equalsIgnoreCase(value.getScheme())
                && value.getHost() != null
                && value.getUserInfo() == null
                && value.getQuery() == null
                && value.getFragment() == null;
    }

    private static String normalizeProviderCode(String value) {
        String normalized = optional(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!PROVIDER_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("providerCode 格式非法");
        }
        return normalized;
    }

    private static String boundedOptional(String value, int maxLength) {
        String checked = optional(value);
        if (checked != null && checked.length() > maxLength) {
            throw new IllegalArgumentException("配置值超过长度上限");
        }
        return checked;
    }

    private static String optional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String safeOptionalSecret(String value) {
        String checked = optional(value);
        if (checked == null) {
            return null;
        }
        return SafeConfigurationValue.requireSecret(
                "wifi.ai.provider.http.api-key",
                checked,
                16,
                Collections.unmodifiableList(Arrays.asList(
                        "change-me",
                        "changeme",
                        "demo-secret",
                        "test-secret",
                        "your-api-key")));
    }

    private static int boundedTimeout(int timeoutMillis, String field) {
        if (timeoutMillis < 100 || timeoutMillis > 120000) {
            throw new IllegalArgumentException(field + " 超过允许范围");
        }
        return timeoutMillis;
    }
}
