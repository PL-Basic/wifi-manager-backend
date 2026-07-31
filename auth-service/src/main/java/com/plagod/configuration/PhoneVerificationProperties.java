package com.plagod.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "verification-code.phone")
public class PhoneVerificationProperties {

    /**
     * local：本地控制台模式。
     * aliyun-number-auth：阿里云号码认证短信认证。
     */
    private String provider = "local";

    /**
     * 单条验证记录最多允许的核验次数。
     */
    private int maxVerifyAttempts = 5;

    private Aliyun aliyun = new Aliyun();

    @Data
    public static class Aliyun {

        private String accessKeyId;
        private String accessKeySecret;

        private String endpoint = "dypnsapi.aliyuncs.com";
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

        private long codeLength = 6L;
        private long codeType = 1L;
        private long duplicatePolicy = 1L;
        private long intervalSeconds = 60L;
        private long validSeconds = 300L;

        private int connectTimeoutMillis = 5000;
        private int readTimeoutMillis = 8000;
    }
}