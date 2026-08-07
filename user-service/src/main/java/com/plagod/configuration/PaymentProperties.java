package com.plagod.configuration;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.exception.ApiStatusException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Data
@Component
@ConfigurationProperties(prefix = "wifi.payment")
public class PaymentProperties {

    private String defaultChannel = "";
    private boolean localDemoEnabled = false;
    private int callbackWindowSeconds = 300;
    private String localDemoSecret = "local-demo-payment-secret-change-me";

    public String normalizeChannel(String channel) {
        String value = StringUtils.hasText(channel) ? channel.trim() : defaultChannel;
        if (!StringUtils.hasText(value)) {
            throw ApiStatusException.serviceUnavailable(
                    "PAYMENT_CHANNEL_UNAVAILABLE：当前服务未提供真实付款渠道");
        }

        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.length() > 32) {
            throw new IllegalArgumentException("支付渠道编码不能超过32个字符");
        }
        return value;
    }

    public int effectiveCallbackWindowSeconds() {
        if (callbackWindowSeconds <= 0) {
            return 300;
        }
        return Math.min(callbackWindowSeconds, 3600);
    }

    public String effectiveLocalDemoSecret() {
        if (!StringUtils.hasText(localDemoSecret)) {
            return "local-demo-payment-secret-change-me";
        }
        return localDemoSecret;
    }
}
