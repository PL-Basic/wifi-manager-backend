package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.configuration.LoginFailProtectionProperties;
import com.plagod.entity.LoginFailRecord;
import com.plagod.mapper.LoginFailRecordMapper;
import com.plagod.service.LoginFailProtectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class LoginFailProtectionServiceImpl implements LoginFailProtectionService {

    @Autowired
    private LoginFailRecordMapper loginFailRecordMapper;
    @Autowired
    private LoginFailProtectionProperties loginFailProtectionProperties;

    //检查当前账号是否被锁
    @Override
    public void checkLocked(String account, String loginType, String requestIp) {
        LoginFailRecord record = findRecord(account, loginType, requestIp);
        if (record == null || record.getLockUntil() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getLockUntil().isAfter(now)) {
            throw new IllegalArgumentException("密码错误次数过多，请稍后再试");
        }

        clearFailure(account, loginType, requestIp);
    }

    @Override
    public void recordFailure(String account, String loginType, String requestIp) {
        String cleanIp = normalizeIp(requestIp);
        LocalDateTime now = LocalDateTime.now();

        LoginFailRecord record = findRecord(account, loginType, cleanIp);

        if (record == null) {
            record = new LoginFailRecord();
            record.setAccount(account);
            record.setLoginType(loginType);
            record.setRequestIp(cleanIp);
            record.setFailCount(1);
            record.setLastFailTime(now);
            loginFailRecordMapper.insert(record);
            return;
        }

        LocalDateTime lastFailTime = record.getLastFailTime();

        if (lastFailTime == null
                || lastFailTime.isBefore(now.minusMinutes(loginFailProtectionProperties.getFailWindowMinutes()))) {
            record.setFailCount(1);
            record.setLastFailTime(now);
            record.setLockUntil(null);
            loginFailRecordMapper.updateById(record);
            return;
        }

        int failCount = record.getFailCount() == null ? 0 : record.getFailCount();
        failCount += 1;

        record.setFailCount(failCount);
        record.setLastFailTime(now);

        if (failCount >= loginFailProtectionProperties.getMaxFailCount()) {
            record.setLockUntil(now.plusMinutes(loginFailProtectionProperties.getLockMinutes()));
        }

        loginFailRecordMapper.updateById(record);
    }

    @Override
    public void clearFailure(String account, String loginType, String requestIp) {
        String cleanIp = normalizeIp(requestIp);

        loginFailRecordMapper.delete(
                new QueryWrapper<LoginFailRecord>()
                .eq("account", account)
                .eq("login_type", loginType)
                .eq("request_ip", cleanIp)
        );
    }

    //获取当前登录IP的账号登录状况
    private LoginFailRecord findRecord(String account, String loginType, String requestIp) {

        String cleanIp = normalizeIp(requestIp);

        return loginFailRecordMapper.selectOne(
                new QueryWrapper<LoginFailRecord>()
                        .eq("account", account)
                        .eq("login_type", loginType)
                        .eq("request_ip", cleanIp)
        );
    }

    private String normalizeIp(String requestIp) {
        return StringUtils.hasText(requestIp) ? requestIp : "unknown";
    }

}
