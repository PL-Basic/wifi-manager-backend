package com.plagod.ai.configuration;

import com.plagod.ai.health.AiProviderHealthIndicator;
import com.plagod.ai.model.AiProviderAvailability;
import com.plagod.ai.observability.AiProviderMetrics;
import com.plagod.ai.provider.AiHttpClientFactory;
import com.plagod.ai.provider.AiModerationProvider;
import com.plagod.ai.provider.AiModerationResultParser;
import com.plagod.ai.provider.AiProviderRegistry;
import com.plagod.ai.provider.HttpAiModerationProvider;
import com.plagod.ai.provider.HttpAiProviderSettings;
import com.plagod.ai.service.AiModerationService;
import com.plagod.ai.support.AiContentSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "wifi.ai.provider.http",
            name = "enabled",
            havingValue = "true")
    public HttpAiModerationProvider httpAiModerationProvider(
            AiProviderProperties properties,
            ObjectProvider<ObjectMapper> objectMappers) {
        HttpAiProviderSettings settings =
                properties.getHttp().toSettings();
        ObjectMapper objectMapper =
                objectMappers.getIfAvailable(ObjectMapper::new);
        return new HttpAiModerationProvider(
                settings,
                AiHttpClientFactory.create(settings),
                objectMapper,
                new AiModerationResultParser(
                        objectMapper,
                        settings.getMaxResponseBytes()));
    }

    @Bean
    @ConditionalOnMissingBean
    public AiProviderRegistry aiProviderRegistry(
            AiProviderProperties properties,
            ObjectProvider<AiModerationProvider> providers) {
        List<AiModerationProvider> availableProviders =
                providers.orderedStream().collect(Collectors.toList());
        AiProviderRegistry registry = new AiProviderRegistry(
                properties.getSelected(),
                availableProviders);
        requireSelectedProviderAvailable(registry);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public AiContentSecurity aiContentSecurity() {
        return new AiContentSecurity();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiProviderMetrics aiProviderMetrics(
            MeterRegistry meterRegistry) {
        return new AiProviderMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiModerationService aiModerationService(
            AiProviderRegistry providerRegistry,
            AiContentSecurity contentSecurity,
            AiProviderMetrics metrics) {
        return new AiModerationService(
                providerRegistry,
                contentSecurity,
                metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiProviderHealthIndicator aiProviderHealthIndicator(
            AiProviderRegistry providerRegistry) {
        return new AiProviderHealthIndicator(providerRegistry);
    }

    private void requireSelectedProviderAvailable(
            AiProviderRegistry registry) {
        if (!registry.selectedProviderCode().isPresent()) {
            return;
        }
        Optional<AiModerationProvider> selected = registry.selected();
        if (!selected.isPresent()
                || availability(selected.get())
                != AiProviderAvailability.AVAILABLE) {
            throw invalidSelectedProvider();
        }
    }

    private AiProviderAvailability availability(
            AiModerationProvider provider) {
        try {
            return provider.availability();
        } catch (RuntimeException exception) {
            throw invalidSelectedProvider();
        }
    }

    private IllegalStateException invalidSelectedProvider() {
        return new IllegalStateException(
                "选中的 AI Provider 不可用或配置无效");
    }
}
