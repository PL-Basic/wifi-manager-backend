package com.plagod.configuration;

import com.plagod.support.SafeConfigurationValue;
import com.plagod.support.StableUnits;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "verification-code")
public class VerificationCodeProperties {
    //每个Target每60秒可以请求一次
    @Min(1)
    @Max(86_400)
    private int targetIntervalSeconds = StableUnits.SECONDS_PER_MINUTE;
    //每个Target每天可以请求20次
    @Min(1)
    @Max(1_000)
    private int targetDailyLimit = 20;
    //每个IP每分钟只能请求10次
    @Min(1)
    @Max(1_000)
    private int ipMinuteLimit = 10;
    //每个IP每天只能请求100次
    @Min(1)
    @Max(10_000)
    private int ipDailyLimit = 100;
    @Min(4)
    @Max(12)
    private int codeLength = 6;
    //过期时间5分钟
    @Min(1)
    @Max(60)
    private int expireMinutes = 5;
    //生成验证码的字符集
    @NotBlank
    @Size(min = 10, max = 128)
    private String codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    //定时清理七天清理一次
    @Min(1)
    @Max(365)
    private int cleanupRetentionDays = 7;
    //表示清理时间
    @NotBlank
    private String cleanupCron = "0 0 3 * * ?";

    @PostConstruct
    public void validateSafeTextConfiguration() {
        SafeConfigurationValue.requireText(
                "verification-code.code-chars",
                codeChars);
        SafeConfigurationValue.requireText(
                "verification-code.cleanup-cron",
                cleanupCron);
    }
}
