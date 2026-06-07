package com.plagod.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "login.fail-protection")
public class LoginFailProtectionProperties {

    //错误登录最大次数
    private Integer maxFailCount = 5;

    //被锁定账号锁定时间
    private Integer lockMinutes = 10;

    //错误时间窗口
    private Integer failWindowMinutes = 10;

    //记录保存日期为三十天
    private Integer recordKeepDays = 30;
}
