package com.plagod.configuration;

import com.plagod.support.SafeConfigurationValue;
import com.plagod.support.StableUnits;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@Component
@Validated
@ConfigurationProperties(prefix = "verification-code.phone")
public class PhoneVerificationProperties {

    private static final String LOCAL_PROVIDER = "local";
    private static final String ALIYUN_PROVIDER = "aliyun-number-auth";
    private static final Set<String> SUPPORTED_PROVIDERS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    LOCAL_PROVIDER,
                    ALIYUN_PROVIDER)));
    private static final Set<String> FORBIDDEN_SECRET_VALUES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "change-me",
                    "example",
                    "secret",
                    "test")));

    /**
     * local：本地控制台模式。
     * aliyun-number-auth：阿里云号码认证短信认证。
     */
    @ToString.Include
    private String provider = "local";

    /**
     * 单条验证记录最多允许的核验次数。
     */
    @ToString.Include
    @Min(1)
    @Max(20)
    private int maxVerifyAttempts = 5;

    private final Environment environment;

    @Valid
    @NotNull
    private Aliyun aliyun = new Aliyun();

    public PhoneVerificationProperties(Environment environment) {
        this.environment = Objects.requireNonNull(
                environment,
                "Spring Environment must not be null");
    }

    @Getter
    @Setter
    @ToString(onlyExplicitlyIncluded = true)
    public static class Aliyun {

        private String accessKeyId;
        private String accessKeySecret;

        @ToString.Include
        private String endpoint = "dypnsapi.aliyuncs.com";
        @ToString.Include
        private String countryCode = "86";

        /**
         * 号码认证控制台中的认证方案名称，可以留空使用默认方案。
         */
        private String schemeName;

        private String signName;
        private String templateCode;

        /**
         * ##code## 由阿里云替换为其生成的验证码。
         */
        private String templateParam = "{\"code\":\"##code##\"}";

        @Min(4)
        @Max(8)
        @ToString.Include
        private long codeLength = 6L;
        @Min(1)
        @Max(2)
        @ToString.Include
        private long codeType = 1L;
        @Min(1)
        @Max(2)
        @ToString.Include
        private long duplicatePolicy = 1L;
        @Min(1)
        @Max(86_400)
        @ToString.Include
        private long intervalSeconds =
                StableUnits.SECONDS_PER_MINUTE;
        @Min(60)
        @Max(1_800)
        @ToString.Include
        private long validSeconds = 300L;

        @Min(100)
        @Max(10L * StableUnits.MILLISECONDS_PER_SECOND)
        @ToString.Include
        private int connectTimeoutMillis = 5000;
        @Min(100)
        @Max(30L * StableUnits.MILLISECONDS_PER_SECOND)
        @ToString.Include
        private int readTimeoutMillis = 8000;
    }

    @PostConstruct
    public void validateSafeConfiguration() {
        String selectedProvider = SafeConfigurationValue.requireText(
                "verification-code.phone.provider",
                provider).trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROVIDERS.contains(selectedProvider)) {
            throw new IllegalStateException(
                    "verification-code.phone.provider is not supported");
        }
        provider = selectedProvider;

        if (LOCAL_PROVIDER.equals(selectedProvider)
                && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "verification-code.phone.provider=local "
                            + "is forbidden in the prod profile");
        }
        if (!ALIYUN_PROVIDER.equals(selectedProvider)) {
            return;
        }
        if (aliyun == null) {
            throw new IllegalStateException(
                    "verification-code.phone.aliyun is required");
        }

        SafeConfigurationValue.requireText(
                "verification-code.phone.aliyun.endpoint",
                aliyun.getEndpoint());
        SafeConfigurationValue.requireText(
                "verification-code.phone.aliyun.country-code",
                aliyun.getCountryCode());
        SafeConfigurationValue.requireText(
                "verification-code.phone.aliyun.sign-name",
                aliyun.getSignName());
        SafeConfigurationValue.requireText(
                "verification-code.phone.aliyun.template-code",
                aliyun.getTemplateCode());
        SafeConfigurationValue.requireText(
                "verification-code.phone.aliyun.template-param",
                aliyun.getTemplateParam());
        validateOptionalSecret(
                "verification-code.phone.aliyun.access-key-id",
                aliyun.getAccessKeyId(),
                8);
        validateOptionalSecret(
                "verification-code.phone.aliyun.access-key-secret",
                aliyun.getAccessKeySecret(),
                16);
    }

    public boolean isSelectedProviderConfigured() {
        if (LOCAL_PROVIDER.equals(provider)) {
            return true;
        }
        return ALIYUN_PROVIDER.equals(provider)
                && aliyun != null
                && hasText(aliyun.getAccessKeyId())
                && hasText(aliyun.getAccessKeySecret())
                && hasText(aliyun.getEndpoint())
                && hasText(aliyun.getCountryCode())
                && hasText(aliyun.getSignName())
                && hasText(aliyun.getTemplateCode())
                && hasText(aliyun.getTemplateParam());
    }

    private void validateOptionalSecret(
            String propertyName,
            String value,
            int minimumLength) {
        if (!hasText(value)) {
            return;
        }
        SafeConfigurationValue.requireSecret(
                propertyName,
                value,
                minimumLength,
                FORBIDDEN_SECRET_VALUES);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
