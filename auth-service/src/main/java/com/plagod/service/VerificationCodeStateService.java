package com.plagod.service;

import com.plagod.configuration.PhoneVerificationProperties;
import com.plagod.entity.auth.VerifyCode;
import com.plagod.mapper.VerifyCodeMapper;
import com.plagod.sender.phone.PhoneVerificationCheckResult;
import com.plagod.sender.phone.PhoneVerificationProvider;
import com.plagod.sender.phone.PhoneVerificationProviderRegistry;
import com.plagod.verification.VerificationCodeTime;
import com.plagod.utils.PasswordUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class VerificationCodeStateService {

    private static final int STATUS_AVAILABLE = 0;
    private static final int STATUS_EXPIRED = 2;
    private static final int VERIFY_STATUS_VERIFIED = 1;
    private static final int VERIFY_LEASE_SECONDS = 60;

    private final VerifyCodeMapper verifyCodeMapper;
    private final PhoneVerificationProviderRegistry providerRegistry;
    private final PhoneVerificationProperties phoneProperties;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate remoteCallTemplate;

    public VerificationCodeStateService(
            VerifyCodeMapper verifyCodeMapper,
            PhoneVerificationProviderRegistry providerRegistry,
            PhoneVerificationProperties phoneProperties,
            PlatformTransactionManager transactionManager) {

        this.verifyCodeMapper = verifyCodeMapper;
        this.providerRegistry = providerRegistry;
        this.phoneProperties = phoneProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.remoteCallTemplate = new TransactionTemplate(transactionManager);
        this.remoteCallTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    /**
     * 供应商调用前后各使用一个短事务，网络调用期间不持有本地事务或行锁。
     */
    public Decision verifyAndRemember(String target, String scene, String submittedCode) {
        PreparedVerification prepared = Objects.requireNonNull(
                transactionTemplate.execute(status ->
                        prepareVerification(target, scene, submittedCode)));
        if (prepared.getDecision() != null) {
            return prepared.getDecision();
        }

        ProviderVerification providerVerification;
        try {
            PhoneVerificationCheckResult result = remoteCallTemplate.execute(
                    status -> {
                        PhoneVerificationProvider provider =
                                providerRegistry.get(
                                        prepared.getProviderName());
                        return provider.verify(
                                prepared.getTarget(),
                                prepared.getProviderOutId(),
                                prepared.getCleanCode(),
                                prepared.getCodeHash());
                    });
            providerVerification = ProviderVerification.completed(result);
        } catch (RuntimeException exception) {
            providerVerification = ProviderVerification.failed();
        }

        ProviderVerification completed = providerVerification;
        return Objects.requireNonNull(transactionTemplate.execute(status ->
                completeProviderVerification(prepared, completed)));
    }

    private PreparedVerification prepareVerification(
            String target,
            String scene,
            String submittedCode) {
        VerifyCode record = verifyCodeMapper.selectLatestUsableForUpdate(target, scene);

        if (record == null) {
            return PreparedVerification.completed(
                    Decision.rejected("验证码不存在或已失效"));
        }

        LocalDateTime now =
                VerificationCodeTime.currentLocalDateTime();

        if (record.getExpireTime() == null || !record.getExpireTime().isAfter(now)) {

            record.setStatus(STATUS_EXPIRED);
            record.setVerifyError("验证码已经过期");
            updateRecord(record);
            return PreparedVerification.completed(
                    Decision.rejected("验证码已经过期"));
        }

        String cleanCode = cleanCode(submittedCode);
        int attempts = safeAttempts(record);
        int maxAttempts = Math.max(1, phoneProperties.getMaxVerifyAttempts());

        if (attempts >= maxAttempts) {
            record.setStatus(STATUS_EXPIRED);
            record.setVerifyError("验证码错误次数过多");
            updateRecord(record);
            return PreparedVerification.completed(
                    Decision.rejected("验证码错误次数过多，请重新获取"));
        }

        if (Integer.valueOf(VERIFY_STATUS_VERIFIED).equals(record.getVerifyStatus())) {

            if (matches(cleanCode, record.getCodeHash())) {
                return PreparedVerification.completed(
                        Decision.verified(record.getId()));
            }

            return PreparedVerification.completed(rejectAttempt(
                    record,
                    attempts + 1,
                    maxAttempts,
                    "CACHED_CHECK",
                    "UNKNOWN",
                    "验证码错误"));
        }

        if ("email".equals(record.getTargetType())) {
            boolean verified = matches(cleanCode, record.getCodeHash());

            return PreparedVerification.completed(finishAttempt(
                    record,
                    cleanCode,
                    attempts + 1,
                    maxAttempts,
                    verified,
                    "LOCAL_HASH",
                    verified ? "PASS" : "UNKNOWN",
                    verified ? null : "验证码错误"));
        }

        if (!"phone".equals(record.getTargetType())) {
            record.setStatus(STATUS_EXPIRED);
            record.setVerifyError("验证码接收方类型无效");
            updateRecord(record);
            return PreparedVerification.completed(
                    Decision.rejected("验证码记录无效"));
        }

        String claimOwner = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plusSeconds(VERIFY_LEASE_SECONDS);
        if (verifyCodeMapper.tryClaimVerification(
                record.getId(),
                claimOwner,
                now,
                leaseUntil) != 1) {
            return PreparedVerification.completed(
                    Decision.providerUnavailable("验证码正在核验，请稍后重试"));
        }

        return PreparedVerification.pending(
                record.getId(),
                record.getTarget(),
                record.getScene(),
                record.getVerificationProvider(),
                record.getProviderOutId(),
                record.getCodeHash(),
                cleanCode,
                claimOwner);
    }

    private Decision completeProviderVerification(
            PreparedVerification prepared,
            ProviderVerification providerVerification) {
        VerifyCode record =
                verifyCodeMapper.selectByIdForUpdate(prepared.getRecordId());
        if (record == null
                || !Integer.valueOf(STATUS_AVAILABLE).equals(record.getStatus())
                || !Objects.equals(
                prepared.getClaimOwner(),
                record.getVerifyClaimOwner())
                || !prepared.matches(record)) {
            return Decision.providerUnavailable("验证码核验状态已变化，请重试");
        }

        releaseClaim(record, prepared.getClaimOwner());

        LocalDateTime now =
                VerificationCodeTime.currentLocalDateTime();
        if (record.getExpireTime() == null
                || !record.getExpireTime().isAfter(now)) {
            record.setStatus(STATUS_EXPIRED);
            record.setVerifyError("验证码已经过期");
            updateRecord(record);
            return Decision.rejected("验证码已经过期");
        }

        int attempts = safeAttempts(record);
        int maxAttempts = Math.max(
                1,
                phoneProperties.getMaxVerifyAttempts());
        if (attempts >= maxAttempts) {
            record.setStatus(STATUS_EXPIRED);
            record.setVerifyError("验证码错误次数过多");
            updateRecord(record);
            return Decision.rejected("验证码错误次数过多，请重新获取");
        }

        if (Integer.valueOf(VERIFY_STATUS_VERIFIED)
                .equals(record.getVerifyStatus())) {
            if (matches(prepared.getCleanCode(), record.getCodeHash())) {
                return Decision.verified(record.getId());
            }
            return rejectAttempt(
                    record,
                    attempts + 1,
                    maxAttempts,
                    "CACHED_CHECK",
                    "UNKNOWN",
                    "验证码错误");
        }

        if (providerVerification.isFailed()) {
            recordProviderFailure(record, "PROVIDER_EXCEPTION", null, "短信认证服务暂时不可用");
            return Decision.providerUnavailable("短信认证服务暂时不可用");
        }

        PhoneVerificationCheckResult result =
                providerVerification.getResult();
        if (result == null || !result.isRequestSuccessful()) {
            recordProviderFailure(record, result == null ? null : result.getProviderCode(), result == null ? null : result.getProviderResult(), result == null ? "短信认证服务没有返回结果" : result.getMessage());

            return Decision.providerUnavailable(result == null || !StringUtils.hasText(result.getMessage()) ? "短信认证服务暂时不可用" : result.getMessage());
        }

        return finishAttempt(
                record,
                prepared.getCleanCode(),
                attempts + 1,
                maxAttempts,
                result.isVerified(),
                result.getProviderCode(),
                result.getProviderResult(),
                result.isVerified() ? null : result.getMessage());
    }

    private Decision finishAttempt(VerifyCode record, String cleanCode, int attempts, int maxAttempts, boolean verified, String providerCode, String providerResult, String error) {

        if (!verified) {
            return rejectAttempt(record, attempts, maxAttempts, providerCode, providerResult, StringUtils.hasText(error) ? error : "验证码错误");
        }

        // 云端验证码在首次核验通过后才生成本地摘要。
        if (!StringUtils.hasText(record.getCodeHash())) {
            record.setCodeHash(PasswordUtils.encode(cleanCode));
        }

        record.setVerifyStatus(VERIFY_STATUS_VERIFIED);
        record.setVerifyAttemptCount(attempts);
        record.setProviderVerifyCode(emptyIfNull(providerCode));
        record.setProviderVerifyResult(emptyIfNull(providerResult));
        record.setVerifyError("");
        record.setVerifyTime(
                VerificationCodeTime.currentLocalDateTime());

        updateRecord(record);
        return Decision.verified(record.getId());
    }

    private Decision rejectAttempt(VerifyCode record, int attempts, int maxAttempts, String providerCode, String providerResult, String error) {

        boolean exhausted = attempts >= maxAttempts;

        record.setVerifyAttemptCount(attempts);
        record.setProviderVerifyCode(emptyIfNull(providerCode));
        record.setProviderVerifyResult(emptyIfNull(providerResult));
        record.setVerifyError(limit(error));

        if (exhausted) {
            record.setStatus(STATUS_EXPIRED);
        }

        updateRecord(record);

        return Decision.rejected(exhausted ? "验证码错误次数过多，请重新获取" : "验证码错误");
    }

    /**
     * 供应商超时或故障不计入用户输错次数。
     */
    private void recordProviderFailure(VerifyCode record, String providerCode, String providerResult, String error) {

        record.setProviderVerifyCode(emptyIfNull(providerCode));
        record.setProviderVerifyResult(emptyIfNull(providerResult));
        record.setVerifyError(limit(error));
        updateRecord(record);
    }

    private void updateRecord(VerifyCode record) {
        if (verifyCodeMapper.updateById(record) != 1) {
            throw new IllegalStateException("验证码状态更新失败");
        }
    }

    private void releaseClaim(VerifyCode record, String claimOwner) {
        if (verifyCodeMapper.releaseVerificationClaim(
                record.getId(),
                claimOwner) != 1) {
            throw new IllegalStateException("验证码核验 Claim 释放失败");
        }
        record.setVerifyClaimOwner(null);
        record.setVerifyLeaseUntil(null);
        record.setVerifyClaimedTime(null);
    }

    private boolean matches(String rawCode, String codeHash) {
        return StringUtils.hasText(codeHash) && PasswordUtils.matches(rawCode, codeHash);
    }

    private String cleanCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        return code.trim().toUpperCase(Locale.ROOT);
    }

    private int safeAttempts(VerifyCode record) {
        return record.getVerifyAttemptCount() == null ? 0 : record.getVerifyAttemptCount();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value) {
        String message = StringUtils.hasText(value) ? value : "验证码核验失败";

        return message.length() > 512 ? message.substring(0, 512) : message;
    }

    @Getter
    @AllArgsConstructor
    public static class Decision {

        private final boolean verified;
        private final boolean providerUnavailable;
        private final Long recordId;
        private final String message;

        public static Decision verified(Long recordId) {
            return new Decision(true, false, recordId, null);
        }

        public static Decision rejected(String message) {
            return new Decision(false, false, null, message);
        }

        public static Decision providerUnavailable(String message) {
            return new Decision(false, true, null, message);
        }
    }

    @Getter
    @AllArgsConstructor
    private static final class PreparedVerification {

        private final Long recordId;
        private final String target;
        private final String scene;
        private final String providerName;
        private final String providerOutId;
        private final String codeHash;
        private final String cleanCode;
        private final String claimOwner;
        private final Decision decision;

        private static PreparedVerification completed(Decision decision) {
            return new PreparedVerification(
                    null, null, null, null, null, null, null, null, decision);
        }

        private static PreparedVerification pending(
                Long recordId,
                String target,
                String scene,
                String providerName,
                String providerOutId,
                String codeHash,
                String cleanCode,
                String claimOwner) {
            return new PreparedVerification(
                    recordId,
                    target,
                    scene,
                    providerName,
                    providerOutId,
                    codeHash,
                    cleanCode,
                    claimOwner,
                    null);
        }

        private boolean matches(VerifyCode record) {
            return Objects.equals(target, record.getTarget())
                    && Objects.equals(scene, record.getScene())
                    && Objects.equals(providerName,
                    record.getVerificationProvider())
                    && Objects.equals(providerOutId,
                    record.getProviderOutId());
        }
    }

    @Getter
    @AllArgsConstructor
    private static final class ProviderVerification {

        private final PhoneVerificationCheckResult result;
        private final boolean failed;

        private static ProviderVerification completed(
                PhoneVerificationCheckResult result) {
            return new ProviderVerification(result, false);
        }

        private static ProviderVerification failed() {
            return new ProviderVerification(null, true);
        }
    }
}
