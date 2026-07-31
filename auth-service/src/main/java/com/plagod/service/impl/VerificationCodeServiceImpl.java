package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.configuration.PhoneVerificationProperties;
import com.plagod.configuration.VerificationCodeProperties;
import com.plagod.entity.auth.VerifyCode;
import com.plagod.exception.VerificationCodeRateLimitException;
import com.plagod.exception.VerificationDeliveryException;
import com.plagod.mapper.VerifyCodeMapper;
import com.plagod.ratelimit.VerificationCodeRedisRateLimiter;
import com.plagod.sender.VerifyCodeSender;
import com.plagod.sender.phone.PhoneVerificationProvider;
import com.plagod.sender.phone.PhoneVerificationProviderRegistry;
import com.plagod.sender.phone.PhoneVerificationSendResult;
import com.plagod.service.VerificationCodeService;
import com.plagod.service.VerificationCodeStateService;
import com.plagod.utils.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Set<String> ALLOWED_SCENES =
            new HashSet<>(Arrays.asList(
                    "register",
                    "login",
                    "reset_password",
                    "bind_contact"
            ));

    private final VerificationCodeProperties properties;
    private final PhoneVerificationProperties phoneProperties;
    private final VerifyCodeMapper verifyCodeMapper;
    private final VerifyCodeSender verifyCodeSender;
    private final PhoneVerificationProviderRegistry providerRegistry;
    private final VerificationCodeStateService stateService;
    private final SecureRandom random = new SecureRandom();
    private final VerificationCodeRedisRateLimiter redisRateLimiter;

    public VerificationCodeServiceImpl(VerificationCodeProperties properties, PhoneVerificationProperties phoneProperties, VerifyCodeMapper verifyCodeMapper, VerifyCodeSender verifyCodeSender, PhoneVerificationProviderRegistry providerRegistry, VerificationCodeStateService stateService, VerificationCodeRedisRateLimiter redisRateLimiter) {

        this.properties = properties;
        this.phoneProperties = phoneProperties;
        this.verifyCodeMapper = verifyCodeMapper;
        this.verifyCodeSender = verifyCodeSender;
        this.providerRegistry = providerRegistry;
        this.stateService = stateService;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    public void sendCode(String target, String scene, String sendIp) {

        String cleanTarget = cleanTarget(target);
        String targetType = resolveTargetType(cleanTarget);
        String cleanScene = cleanScene(scene);
        LocalDateTime now = LocalDateTime.now();

        /*
         * Redis 正常时执行多实例原子检查；
         * Redis 故障时继续使用现有 MySQL 记录完成降级检查。
         */
        if (!redisRateLimiter.acquire(cleanTarget, cleanScene, sendIp, now)) {
            checkSendLimit(cleanTarget, cleanScene, sendIp, now);
        }

        PhoneVerificationProvider phoneProvider = null;
        String rawEmailCode = null;
        String providerName;
        String outId = null;

        if ("phone".equals(targetType)) {
            phoneProvider = providerRegistry.current();
            providerName = phoneProvider.providerName();
            outId = UUID.randomUUID().toString();
        } else {
            providerName = "email-smtp";
            rawEmailCode = generateLocalCode();
        }

        VerifyCode record = new VerifyCode();
        record.setTarget(cleanTarget);
        record.setTargetType(targetType);
        record.setScene(cleanScene);
        record.setVerificationProvider(providerName);
        record.setProviderOutId(outId);

        if (rawEmailCode != null) {
            record.setCodeHash(PasswordUtils.encode(rawEmailCode));
        }

        record.setSendStatus(0);
        record.setVerifyStatus(0);
        record.setVerifyAttemptCount(0);
        record.setStatus(0);
        record.setExpireTime(resolveExpireTime(now, providerName));
        record.setSendIp(sendIp);

        if (verifyCodeMapper.insert(record) != 1) {
            throw new IllegalStateException("验证码发送记录创建失败");
        }

        try {
            if ("email".equals(targetType)) {
                verifyCodeSender.send(cleanTarget, targetType, cleanScene, rawEmailCode);

                markSendSuccess(record, "SMTP_OK");
            } else {
                sendPhoneCode(record, phoneProvider, cleanTarget, cleanScene, outId);
            }

            log.info("验证码发送成功 recordId={}, targetType={}, scene={}, provider={}", record.getId(), targetType, cleanScene, providerName);
        } catch (VerificationDeliveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            markSendFailure(record, "PROVIDER_EXCEPTION", limitMessage(exception));

            log.error("验证码发送异常 recordId={}, targetType={}, scene={}, provider={}", record.getId(), targetType, cleanScene, providerName, exception);

            throw new VerificationDeliveryException("验证码发送服务暂时不可用", exception);
        }
    }

    private void sendPhoneCode(VerifyCode record, PhoneVerificationProvider provider, String phone, String scene, String outId) {

        PhoneVerificationSendResult result = provider.send(phone, scene, outId);

        if (result != null) {
            record.setProviderRequestId(result.getRequestId());
            record.setProviderBizId(result.getBizId());
            record.setProviderSendCode(emptyIfNull(result.getProviderCode()));

            if (StringUtils.hasText(result.getLocalCodeHash())) {
                record.setCodeHash(result.getLocalCodeHash());
            }
        }

        boolean successful = result != null && result.isSuccessful() && provider.providerName().equals(result.getProvider()) && outId.equals(result.getOutId());

        // 本地 Provider 必须返回摘要，云端 Provider 不返回明文或摘要。
        if (successful && "local".equals(provider.providerName()) && !StringUtils.hasText(record.getCodeHash())) {

            successful = false;
        }

        if (!successful) {
            String providerCode = result == null ? "EMPTY_RESPONSE" : result.getProviderCode();

            String message = result == null ? "短信供应商没有返回发送结果" : result.getMessage();

            markSendFailure(record, providerCode, message);

            throw new VerificationDeliveryException(StringUtils.hasText(message) ? message : "短信发送失败");
        }

        markSendSuccess(record, result.getProviderCode());
    }

    @Override
    public void checkCode(String target, String scene, String code) {

        VerificationCodeStateService.Decision decision = verifyAndRemember(target, scene, code);

        requireVerified(decision);
    }

    @Override
    @Transactional
    public void consumeCode(String target, String scene, String code, String verifyIp) {

        VerificationCodeStateService.Decision decision = verifyAndRemember(target, scene, code);

        requireVerified(decision);

        LocalDateTime now = LocalDateTime.now();

        int affected = verifyCodeMapper.consumeVerifiedCode(decision.getRecordId(), now, verifyIp);

        if (affected != 1) {
            throw new IllegalArgumentException("验证码已被使用或已经过期");
        }
    }

    private VerificationCodeStateService.Decision verifyAndRemember(String target, String scene, String code) {

        String cleanTarget = cleanTarget(target);
        resolveTargetType(cleanTarget);
        String cleanScene = cleanScene(scene);

        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        return stateService.verifyAndRemember(cleanTarget, cleanScene, code);
    }

    private void requireVerified(VerificationCodeStateService.Decision decision) {

        if (decision != null && decision.isVerified() && decision.getRecordId() != null) {
            return;
        }

        String message = decision == null || !StringUtils.hasText(decision.getMessage()) ? "验证码核验失败" : decision.getMessage();

        if (decision != null && decision.isProviderUnavailable()) {
            throw new VerificationDeliveryException(message);
        }

        throw new IllegalArgumentException(message);
    }

    private void markSendSuccess(VerifyCode record, String providerCode) {

        record.setProviderSendCode(emptyIfNull(providerCode));
        record.setSendStatus(1);
        record.setSendTime(LocalDateTime.now());
        record.setSendError("");

        updateRecord(record);
    }

    private void markSendFailure(VerifyCode record, String providerCode, String message) {

        record.setProviderSendCode(emptyIfNull(providerCode));
        record.setSendStatus(2);
        record.setSendTime(LocalDateTime.now());
        record.setSendError(limitMessage(message));

        updateRecord(record);
    }

    private void updateRecord(VerifyCode record) {
        if (verifyCodeMapper.updateById(record) != 1) {
            throw new IllegalStateException("验证码发送状态更新失败");
        }
    }

    private LocalDateTime resolveExpireTime(LocalDateTime now, String providerName) {

        if ("aliyun-number-auth".equals(providerName)) {
            long validSeconds = phoneProperties.getAliyun().getValidSeconds();

            if (validSeconds < 1) {
                throw new VerificationDeliveryException("阿里云验证码有效期配置无效");
            }

            return now.plusSeconds(validSeconds);
        }

        if (properties.getExpireMinutes() < 1) {
            throw new IllegalStateException("本地验证码有效期配置无效");
        }

        return now.plusMinutes(properties.getExpireMinutes());
    }

    private void checkSendLimit(String target, String scene, String sendIp, LocalDateTime now) {

        LocalDateTime intervalStart = now.minusSeconds(properties.getTargetIntervalSeconds());

        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

        Long recentTargetCount = verifyCodeMapper.selectCount(
                new QueryWrapper<VerifyCode>()
                        .eq("target", target)
                        .eq("scene", scene)
                        .ge("create_time", intervalStart)
        );

        if (recentTargetCount != null && recentTargetCount > 0) {
            throw new VerificationCodeRateLimitException("验证码发送太频繁，请稍后再试", properties.getTargetIntervalSeconds());
        }

        Long targetTodayCount = verifyCodeMapper.selectCount(
                new QueryWrapper<VerifyCode>()
                        .eq("target", target)
                        .eq("scene", scene)
                        .ge("create_time", todayStart)
        );

        if (targetTodayCount != null && targetTodayCount >= properties.getTargetDailyLimit()) {

            throw new VerificationCodeRateLimitException("今日验证码发送次数已达上限", secondsUntilTomorrow(now));
        }

        if (!StringUtils.hasText(sendIp)) {
            return;
        }

        Long ipMinuteCount = verifyCodeMapper.selectCount(
                new QueryWrapper<VerifyCode>()
                        .eq("send_ip", sendIp)
                        .eq("scene", scene)
                        .ge("create_time", now.minusMinutes(1))
        );

        if (ipMinuteCount != null && ipMinuteCount >= properties.getIpMinuteLimit()) {
            throw new VerificationCodeRateLimitException("验证码发送太频繁，请稍后再试", 60L);
        }

        Long ipTodayCount = verifyCodeMapper.selectCount(
                new QueryWrapper<VerifyCode>()
                        .eq("send_ip", sendIp)
                        .eq("scene", scene)
                        .ge("create_time", todayStart)
        );

        if (ipTodayCount != null && ipTodayCount >= properties.getIpDailyLimit()) {
            throw new VerificationCodeRateLimitException("当前网络验证码请求次数已达上限", secondsUntilTomorrow(now));
        }
    }

    private String generateLocalCode() {
        String chars = properties.getCodeChars();
        int length = properties.getCodeLength();

        if (!StringUtils.hasText(chars) || length < 1) {
            throw new IllegalStateException("本地验证码生成配置无效");
        }

        StringBuilder result = new StringBuilder(length);

        for (int index = 0; index < length; index++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }

        return result.toString();
    }

    private String cleanTarget(String target) {
        if (!StringUtils.hasText(target)) {
            throw new IllegalArgumentException("手机号或者邮箱不能为空");
        }

        return target.trim();
    }

    private String resolveTargetType(String target) {
        if (PHONE_PATTERN.matcher(target).matches()) {
            return "phone";
        }

        if (EMAIL_PATTERN.matcher(target).matches()) {
            return "email";
        }

        throw new IllegalArgumentException("手机号或邮箱格式不正确");
    }

    private String cleanScene(String scene) {
        if (!StringUtils.hasText(scene) || !ALLOWED_SCENES.contains(scene.trim())) {

            throw new IllegalArgumentException("验证场景不正确");
        }

        return scene.trim();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String limitMessage(Throwable throwable) {
        if (throwable == null) {
            return "验证码发送失败";
        }

        return limitMessage(throwable.getMessage());
    }

    private String limitMessage(String message) {
        String result = StringUtils.hasText(message) ? message : "验证码发送失败";

        return result.length() > 512 ? result.substring(0, 512) : result;
    }

    private long secondsUntilTomorrow(LocalDateTime now) {
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();

        return Math.max(1L, Duration.between(now, tomorrow).getSeconds());
    }
}