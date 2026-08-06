package com.plagod.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "wifi.auth-session")
public class AuthSessionProperties {

    private Duration refreshAbsoluteTtl = Duration.ofDays(7);
    private Duration operationTokenTtl = Duration.ofMinutes(5);
    private String cookieName = "wifi_refresh";
    private String cookiePath = "/";
    private String sameSite = "Lax";
    private boolean secureCookie;

    @PostConstruct
    public void validate() {
        if (!Duration.ofDays(7).equals(refreshAbsoluteTtl)) {
            throw new IllegalStateException("P-2 要求 Refresh Session 固定为7天绝对有效期");
        }
        if (operationTokenTtl == null || operationTokenTtl.isZero() || operationTokenTtl.isNegative()) {
            throw new IllegalStateException("一次性操作凭证有效期必须大于0");
        }
        if (cookieName == null || cookieName.trim().isEmpty()) {
            throw new IllegalStateException("Refresh Cookie 名称不能为空");
        }
        if (!"Lax".equalsIgnoreCase(sameSite)
                && !"Strict".equalsIgnoreCase(sameSite)
                && !"None".equalsIgnoreCase(sameSite)) {
            throw new IllegalStateException("Refresh Cookie SameSite 配置无效");
        }
        if ("None".equalsIgnoreCase(sameSite) && !secureCookie) {
            throw new IllegalStateException("SameSite=None 必须同时启用 Secure");
        }
    }
}
