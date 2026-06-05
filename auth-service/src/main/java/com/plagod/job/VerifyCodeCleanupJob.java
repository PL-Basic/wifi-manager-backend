package com.plagod.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.configuration.VerificationCodeProperties;
import com.plagod.entity.VerifyCode;
import com.plagod.mapper.VerifyCodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;

@Component
public class VerifyCodeCleanupJob {

    @Autowired
    private VerifyCodeMapper verifyCodeMapper;

    @Autowired
    private VerificationCodeProperties verificationCodeProperties;

    @Scheduled(cron = "${verification-code.cleanup-cron:0 0 3 * * ?}")
    public void cleanupExpireVerifyCodes() {
        int retentionDays = verificationCodeProperties.getCleanupRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);

        verifyCodeMapper.delete(
                new QueryWrapper<VerifyCode>()
                        .lt("create_time", before)
        );

    }

}
