package com.plagod.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.configuration.LoginFailProtectionProperties;
import com.plagod.entity.LoginFailRecord;
import com.plagod.mapper.LoginFailRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class LoginFailRecordCleanupJob {

    @Autowired
    private LoginFailRecordMapper loginFailRecordMapper;

    @Autowired
    private LoginFailProtectionProperties loginFailProtectionProperties;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredRecords() {
        LocalDateTime expiredTime = LocalDateTime.now().minusDays(loginFailProtectionProperties.getRecordKeepDays());

        int deleted = loginFailRecordMapper.delete(
                new QueryWrapper<LoginFailRecord>()
                .lt("update_time", expiredTime)
        );

        if (deleted > 0) {
            log.info("清理登录失败记录完成，删除数量：{}", deleted);
        }
    }
}
