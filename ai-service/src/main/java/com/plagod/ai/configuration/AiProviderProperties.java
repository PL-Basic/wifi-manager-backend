package com.plagod.ai.configuration;

import com.plagod.ai.provider.HttpAiProviderSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "wifi.ai.provider")
public final class AiProviderProperties {

    private String selected;
    private final Http http = new Http();

    public String getSelected() {
        return selected;
    }

    public void setSelected(String selected) {
        this.selected = selected;
    }

    public Http getHttp() {
        return http;
    }

    public static final class Http {

        private boolean enabled;
        private String providerCode = "http";
        private String endpoint;
        private String model;
        private String apiKey;
        private int connectTimeoutMillis = 3000;
        private int readTimeoutMillis = 10000;
        private int maxResponseBytes = 65536;
        private boolean noTrainingSupported;
        private String retentionMode = "UNKNOWN";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }

        public int getMaxResponseBytes() {
            return maxResponseBytes;
        }

        public void setMaxResponseBytes(int maxResponseBytes) {
            this.maxResponseBytes = maxResponseBytes;
        }

        public boolean isNoTrainingSupported() {
            return noTrainingSupported;
        }

        public void setNoTrainingSupported(boolean noTrainingSupported) {
            this.noTrainingSupported = noTrainingSupported;
        }

        public String getRetentionMode() {
            return retentionMode;
        }

        public void setRetentionMode(String retentionMode) {
            this.retentionMode = retentionMode;
        }

        HttpAiProviderSettings toSettings() {
            try {
                return new HttpAiProviderSettings(
                        enabled,
                        providerCode,
                        optionalUri(endpoint),
                        model,
                        apiKey,
                        connectTimeoutMillis,
                        readTimeoutMillis,
                        maxResponseBytes,
                        noTrainingSupported,
                        retentionMode);
            } catch (RuntimeException exception) {
                throw invalidConfiguration();
            }
        }

        private URI optionalUri(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            return URI.create(value.trim());
        }

        private IllegalStateException invalidConfiguration() {
            return new IllegalStateException(
                    "AI HTTP Provider 配置无效");
        }
    }
}
