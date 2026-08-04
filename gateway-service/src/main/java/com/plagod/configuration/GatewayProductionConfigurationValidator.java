package com.plagod.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@Profile("prod")
public class GatewayProductionConfigurationValidator {

    private final String gatewayToken;
    private final boolean redisRateLimitEnabled;

    public GatewayProductionConfigurationValidator(
            @Value("${wifi.security.gateway-token}") String gatewayToken,
            @Value("${wifi.rate-limit.redis-enabled}") boolean redisRateLimitEnabled) {
        this.gatewayToken = gatewayToken;
        this.redisRateLimitEnabled = redisRateLimitEnabled;
    }

    @PostConstruct
    void validate() {
        validateGatewayToken(gatewayToken);

        if (!redisRateLimitEnabled) {
            throw new IllegalStateException("生产环境必须启用 Redis 全局限流");
        }
    }

    private void validateGatewayToken(String token) {
        if (!StringUtils.hasText(token) || token.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("Gateway Token 必须配置且不能少于 16 字节");
        }

        if (!token.equals(token.trim())) {
            throw new IllegalStateException("Gateway Token 首尾不能包含空白字符");
        }

        String upperToken = token.toUpperCase(Locale.ROOT);
        if (upperToken.contains("CHANGE_ME")
                || upperToken.contains("CHANGE-ME")
                || upperToken.contains("LOCAL-GATEWAY-TOKEN")) {
            throw new IllegalStateException("Gateway Token 仍是示例值，生产环境拒绝启动");
        }
    }
}
