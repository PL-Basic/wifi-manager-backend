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
import com.plagod.support.StructuredRedactor;
import com.plagod.utils.PasswordUtils;
import com.plagod.verification.VerificationCodeTime;
import com.plagod.web.SafeExceptionLogFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern SAFE_PROVIDER_CODE =
            Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    private static final Set<String> ALLOWED_SCENES =
            new HashSet<>(Arrays.asList(
                    "register",
                    "login",
                    "reset_password",
                    "bind_contact",
                    "step_up"
            ));
    private static final Set<String> SEND_ERROR_FIELDS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "provider",
                    "providerCode",
                    "message")));
    private static final Set<String> SEND_ERROR_REDACTED_FIELDS =
            Collections.singleton("message");

    private final VerificationCodeProperties properties;
    private final PhoneVerificationProperties phoneProperties;
    private final VerifyCodeMapper verifyCodeMapper;
    private final VerifyCodeSender verifyCodeSender;
    private final PhoneVerificationProviderRegistry providerRegistry;
    private final VerificationCodeStateService stateService;
    private final SecureRandom random = new SecureRandom();
    private final VerificationCodeRedisRateLimiter redisRateLimiter;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate sendTransactionTemplate;
    private final TransactionTemplate remoteCallTemplate;

    public VerificationCodeServiceImpl(
            VerificationCodeProperties properties,
            PhoneVerificationProperties phoneProperties,
            VerifyCodeMapper verifyCodeMapper,
            VerifyCodeSender verifyCodeSender,
            PhoneVerificationProviderRegistry providerRegistry,
            VerificationCodeStateService stateService,
            VerificationCodeRedisRateLimiter redisRateLimiter,
            PlatformTransactionManager transactionManager) {

        this.properties = properties;
        this.phoneProperties = phoneProperties;
        this.verifyCodeMapper = verifyCodeMapper;
        this.verifyCodeSender = verifyCodeSender;
        this.providerRegistry = providerRegistry;
        this.stateService = stateService;
        this.redisRateLimiter = redisRateLimiter;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
        this.sendTransactionTemplate =
                new TransactionTemplate(transactionManager);
        this.sendTransactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.remoteCallTemplate =
                new TransactionTemplate(transactionManager);
        this.remoteCallTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Override
    public void sendCode(String target, String scene, String sendIp) {

        String cleanTarget = cleanTarget(target);
        String targetType = resolveTargetType(cleanTarget);
        String cleanScene = cleanScene(scene);
        ZonedDateTime businessNow = VerificationCodeTime.now();
        LocalDateTime now =
                VerificationCodeTime.localDateTime(businessNow);

        /*
         * Redis 正常时执行多实例原子检查；
         * Redis 故障时继续使用现有 MySQL 记录完成降级检查。
         */
        if (!redisRateLimiter.acquire(
                cleanTarget,
                cleanScene,
                sendIp,
                businessNow)) {
            checkSendLimit(
                    cleanTarget,
                    cleanScene,
                    sendIp,
                    businessNow);
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

        sendTransactionTemplate.execute(status -> {
            if (verifyCodeMapper.insert(record) != 1) {
                throw new IllegalStateException("验证码发送记录创建失败");
            }
            return null;
        });

        try {
            if ("email".equals(targetType)) {
                String emailCode = rawEmailCode;
                remoteCallTemplate.execute(status -> {
                    verifyCodeSender.send(
                            cleanTarget,
                            targetType,
                            cleanScene,
                            emailCode);
                    return null;
                });

                markSendSuccess(record, "SMTP_OK");
            } else {
                sendPhoneCode(record, phoneProvider, cleanTarget, cleanScene, outId);
            }

            log.info("验证码发送成功 recordId={}, targetType={}, scene={}, provider={}", record.getId(), targetType, cleanScene, providerName);
        } catch (VerificationDeliveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            markSendFailure(
                    record,
                    "PROVIDER_EXCEPTION",
                    exception.getMessage());

            log.error(
                    "验证码发送异常 recordId={}, targetType={}, scene={}, "
                            + "provider={}, exceptionType={}, safeStack={}",
                    record.getId(),
                    targetType,
                    cleanScene,
                    providerName,
                    exception.getClass().getName(),
                    SafeExceptionLogFormatter.format(exception));

            throw new VerificationDeliveryException("验证码发送服务暂时不可用", exception);
        }
    }

    private void sendPhoneCode(VerifyCode record, PhoneVerificationProvider provider, String phone, String scene, String outId) {

        PhoneVerificationSendResult result = remoteCallTemplate.execute(
                status -> provider.send(phone, scene, outId));

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

            throw new VerificationDeliveryException(
                    "验证码发送服务暂时不可用");
        }

        markSendSuccess(record, result.getProviderCode());
    }

    @Override
    public void checkCode(String target, String scene, String code) {

        VerificationCodeStateService.Decision decision = verifyAndRemember(target, scene, code);

        requireVerified(decision);
    }

    @Override
    public boolean checkCodeForRequest(
            String target,
            String scene,
            String code,
            String consumeRequestKey) {
        if (wasConsumedByRequest(target, scene, consumeRequestKey)) {
            return true;
        }
        checkCode(target, scene, code);
        return false;
    }

    @Override
    public void consumeCode(String target, String scene, String code, String verifyIp) {
        consumeCodeForRequest(target, scene, code, verifyIp, null);
    }

    @Override
    public void consumeCodeForRequest(
            String target,
            String scene,
            String code,
            String verifyIp,
            String consumeRequestKey) {
        if (StringUtils.hasText(consumeRequestKey)
                && wasConsumedByRequest(target, scene, consumeRequestKey)) {
            return;
        }
        VerificationCodeStateService.Decision decision = verifyAndRemember(target, scene, code);

        requireVerified(decision);

        LocalDateTime now =
                VerificationCodeTime.currentLocalDateTime();

        transactionTemplate.execute(status -> {
            int affected = verifyCodeMapper.consumeVerifiedCode(
                    decision.getRecordId(),
                    now,
                    verifyIp,
                    StringUtils.hasText(consumeRequestKey)
                            ? consumeRequestKey.trim()
                            : null);

            if (affected != 1
                    && !(StringUtils.hasText(consumeRequestKey)
                    && wasConsumedByRequest(
                    target,
                    scene,
                    consumeRequestKey))) {
                throw new IllegalArgumentException(
                        "验证码已被使用或已经过期");
            }
            return null;
        });
    }

    private boolean wasConsumedByRequest(
            String target,
            String scene,
            String consumeRequestKey) {
        if (!StringUtils.hasText(consumeRequestKey)) {
            return false;
        }
        String cleanTarget = cleanTarget(target);
        resolveTargetType(cleanTarget);
        String cleanScene = cleanScene(scene);
        return verifyCodeMapper.selectOne(
                new QueryWrapper<VerifyCode>()
                        .eq("target", cleanTarget)
                        .eq("scene", cleanScene)
                        .eq("consume_request_key", consumeRequestKey.trim())
                        .eq("status", 1)) != null;
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

        record.setProviderSendCode(StringUtils.hasText(providerCode)
                ? safeProviderCode(providerCode)
                : "");
        record.setSendStatus(1);
        record.setSendTime(
                VerificationCodeTime.currentLocalDateTime());
        record.setSendError("");

        Integer affected = sendTransactionTemplate.execute(status ->
                verifyCodeMapper.finalizeSendSuccess(record));
        if (!Integer.valueOf(1).equals(affected)) {
            throw new IllegalStateException("验证码发送成功状态已发生变化");
        }
    }

    private void markSendFailure(VerifyCode record, String providerCode, String message) {

        String safeProviderCode = safeProviderCode(providerCode);
        record.setProviderSendCode(safeProviderCode);
        record.setSendStatus(2);
        record.setSendTime(
                VerificationCodeTime.currentLocalDateTime());
        record.setSendError(safeSendError(
                record.getVerificationProvider(),
                safeProviderCode,
                message));

        Integer affected = sendTransactionTemplate.execute(status ->
                verifyCodeMapper.finalizeSendFailure(record));
        if (!Integer.valueOf(1).equals(affected)) {
            throw new IllegalStateException("验证码发送失败状态已发生变化");
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

    private void checkSendLimit(
            String target,
            String scene,
            String sendIp,
            ZonedDateTime businessNow) {

        LocalDateTime now =
                VerificationCodeTime.localDateTime(businessNow);
        LocalDateTime intervalStart = now.minusSeconds(properties.getTargetIntervalSeconds());

        LocalDateTime todayStart =
                VerificationCodeTime.startOfDay(businessNow);

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

            throw new VerificationCodeRateLimitException(
                    "今日验证码发送次数已达上限",
                    VerificationCodeTime.secondsUntilNextDay(
                            businessNow));
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
            throw new VerificationCodeRateLimitException(
                    "当前网络验证码请求次数已达上限",
                    VerificationCodeTime.secondsUntilNextDay(
                            businessNow));
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

    private String safeProviderCode(String providerCode) {
        if (StringUtils.hasText(providerCode)
                && SAFE_PROVIDER_CODE.matcher(providerCode).matches()) {
            return providerCode;
        }
        return "PROVIDER_FAILURE";
    }

    private String safeSendError(
            String provider,
            String providerCode,
            String message) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("provider", safeProviderCode(provider));
        fields.put("providerCode", providerCode);
        fields.put("message", message);
        return StructuredRedactor.redact(
                fields,
                SEND_ERROR_FIELDS,
                SEND_ERROR_REDACTED_FIELDS).toString();
    }

}
