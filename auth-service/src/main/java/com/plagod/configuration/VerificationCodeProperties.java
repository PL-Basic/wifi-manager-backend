package com.plagod.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "verification-code")
public class VerificationCodeProperties {
    //每个Target每60秒可以请求一次
    private int targetIntervalSeconds = 60;
    //每个Target每天可以请求20次
    private int targetDailyLimit = 20;
    //每个IP每分钟只能请求10次
    private int ipMinuteLimit = 10;
    //每个IP每天只能请求100次
    private int ipDailyLimit = 100;
    private int codeLength = 6;
    //过期时间5分钟
    private int expireMinutes = 5;
    //生成验证码的字符集
    private String codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

}
