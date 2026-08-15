package com.plagod.configuration;

import com.plagod.exception.ApiStatusException;
import com.plagod.support.SafeConfigurationValue;
import com.plagod.support.StableUnits;
import com.plagod.support.StructuredRedactor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "wifi.payment")
public class PaymentProperties
        implements InitializingBean, EnvironmentAware {

    private static final int DEFAULT_CALLBACK_WINDOW_SECONDS =
            5 * StableUnits.SECONDS_PER_MINUTE;
    private static final int MAX_CALLBACK_WINDOW_SECONDS =
            60 * StableUnits.SECONDS_PER_MINUTE;
    private static final String LOCAL_DEMO_SECRET_PROPERTY =
            "wifi.payment.local-demo-secret";
    private static final String EXAMPLE_LOCAL_DEMO_SECRET =
            "local-demo-payment-secret-change-me";
    private static final Set<String> SAFE_TO_STRING_KEYS =
            Collections.unmodifiableSet(
                    new java.util.LinkedHashSet<>(Arrays.asList(
                            "defaultChannel",
                            "localDemoEnabled",
                            "callbackWindowSeconds",
                            "localDemoSecret")));
    private static final Set<String> SECRET_TO_STRING_KEYS =
            Collections.singleton("localDemoSecret");

    private String defaultChannel = "";
    private boolean localDemoEnabled = false;
    private int callbackWindowSeconds =
            DEFAULT_CALLBACK_WINDOW_SECONDS;
    private String localDemoSecret = EXAMPLE_LOCAL_DEMO_SECRET;
    private transient Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (callbackWindowSeconds < 1
                || callbackWindowSeconds
                > MAX_CALLBACK_WINDOW_SECONDS) {
            throw new IllegalStateException(
                    "wifi.payment.callback-window-seconds "
                            + "must be between 1 and "
                            + MAX_CALLBACK_WINDOW_SECONDS);
        }
        if (localDemoEnabled) {
            if (environment != null
                    && environment.acceptsProfiles(
                    Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "wifi.payment.local-demo-enabled "
                                + "must be false in prod");
            }
            SafeConfigurationValue.requireSecret(
                    LOCAL_DEMO_SECRET_PROPERTY,
                    localDemoSecret,
                    16,
                    Collections.singleton(EXAMPLE_LOCAL_DEMO_SECRET));
        }
    }

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
        return callbackWindowSeconds;
    }

    public String effectiveLocalDemoSecret() {
        return localDemoSecret;
    }

    @Override
    public String toString() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("defaultChannel", defaultChannel);
        values.put("localDemoEnabled", localDemoEnabled);
        values.put(
                "callbackWindowSeconds",
                callbackWindowSeconds);
        values.put("localDemoSecret", localDemoSecret);
        return "PaymentProperties"
                + StructuredRedactor.redact(
                values,
                SAFE_TO_STRING_KEYS,
                SECRET_TO_STRING_KEYS);
    }
}
