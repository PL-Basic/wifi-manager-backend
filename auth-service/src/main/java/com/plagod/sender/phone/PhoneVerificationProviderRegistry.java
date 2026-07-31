package com.plagod.sender.phone;

import com.plagod.configuration.PhoneVerificationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PhoneVerificationProviderRegistry {

    private final PhoneVerificationProperties properties;
    private final Map<String, PhoneVerificationProvider> providers = new HashMap<>();

    public PhoneVerificationProviderRegistry(PhoneVerificationProperties properties, List<PhoneVerificationProvider> providerList) {

        this.properties = properties;

        for (PhoneVerificationProvider provider : providerList) {
            String providerName = normalize(provider.providerName());
            PhoneVerificationProvider previous = providers.put(providerName, provider);

            if (previous != null) {
                throw new IllegalStateException("手机号认证 Provider 重复：" + providerName);
            }
        }
    }

    /**
     * 发送新验证码时使用当前配置的 Provider。
     */
    public PhoneVerificationProvider current() {
        return get(properties.getProvider());
    }

    /**
     * 核验已有验证码时，必须传入记录中保存的 Provider，
     * 防止配置切换后旧验证码被交给错误的供应商核验。
     */
    public PhoneVerificationProvider get(String providerName) {
        String normalized = normalize(providerName);
        PhoneVerificationProvider provider = providers.get(normalized);

        if (provider == null) {
            throw new IllegalStateException("不支持的手机号认证 Provider：" + normalized);
        }

        return provider;
    }

    private String normalize(String providerName) {
        if (!StringUtils.hasText(providerName)) {
            // 兼容迁移前产生的本地验证码记录。
            return "local";
        }

        return providerName.trim().toLowerCase(Locale.ROOT);
    }
}