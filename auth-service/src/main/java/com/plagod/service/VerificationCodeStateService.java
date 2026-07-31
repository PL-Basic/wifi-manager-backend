package com.plagod.service;

import com.plagod.configuration.PhoneVerificationProperties;
import com.plagod.entity.auth.VerifyCode;
import com.plagod.mapper.VerifyCodeMapper;
import com.plagod.sender.phone.PhoneVerificationCheckResult;
import com.plagod.sender.phone.PhoneVerificationProvider;
import com.plagod.sender.phone.PhoneVerificationProviderRegistry;
import com.plagod.utils.PasswordUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class VerificationCodeStateService {

    private static final int STATUS_AVAILABLE = 0;
    private static final int STATUS_EXPIRED = 2;
    private static final int VERIFY_STATUS_VERIFIED = 1;

    private final VerifyCodeMapper verifyCodeMapper;
    private final PhoneVerificationProviderRegistry providerRegistry;
    private final PhoneVerificationProperties phoneProperties;

    public VerificationCodeStateService(VerifyCodeMapper verifyCodeMapper, PhoneVerificationProviderRegistry providerRegistry, PhoneVerificationProperties phoneProperties) {

        this.verifyCodeMapper = verifyCodeMapper;
        this.providerRegistry = providerRegistry;
        this.phoneProperties = phoneProperties;
    }

    /**
     * 使用独立事务保存核验结果。
     * 即使后续注册或重置密码失败，已通过的供应商核验也不会回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Decision verifyAndRemember(String target, String scene, String submittedCode) {

        VerifyCode record = verifyCodeMapper.selectLatestUsableForUpdate(target, scene);

        if (record == null) {
            return Decision.rejected("验证码不存在或已失效");
        }

        LocalDateTime now = LocalDateTime.now();

        if (record.getExpireTime() == null || !record.getExpireTime().isAfter(now)) {

            record.setStatus(STATUS_EXPIRED);
            record.setVerifyError("验证码已经过期");
            updateRecord(record);
            return Decision.rejected("验证码已经过期");
        }

        String cleanCode = cleanCode(submittedCode);
        int attempts = safeAttempts(record);
        int maxAttempts = Math.max(1, phoneProperties.getMaxVerifyAttempts());

        if (attempts >= maxAttempts) {
            record.setStatus(STATUS_EXPIRED);
            record.setVerifyError("验证码错误次数过多");
            updateRecord(record);
            return Decision.rejected("验证码错误次数过多，请重新获取");
        }

        // 已经通过供应商核验时，只比较本地摘要，不再次调用外部接口。
        if (Integer.valueOf(VERIFY_STATUS_VERIFIED).equals(record.getVerifyStatus())) {

            if (matches(cleanCode, record.getCodeHash())) {
                return Decision.verified(record.getId());
            }

            return rejectAttempt(record, attempts + 1, maxAttempts, "CACHED_CHECK", "UNKNOWN", "验证码错误");
        }

        if ("email".equals(record.getTargetType())) {
            boolean verified = matches(cleanCode, record.getCodeHash());

            return finishAttempt(record, cleanCode, attempts + 1, maxAttempts, verified, "LOCAL_HASH", verified ? "PASS" : "UNKNOWN", verified ? null : "验证码错误");
        }

        if (!"phone".equals(record.getTargetType())) {
            record.setStatus(STATUS_EXPIRED);
            record.setVerifyError("验证码接收方类型无效");
            updateRecord(record);
            return Decision.rejected("验证码记录无效");
        }

        PhoneVerificationCheckResult result;

        try {
            // 必须按发送记录中的 Provider 核验，不能使用当前配置覆盖旧记录。
            PhoneVerificationProvider provider = providerRegistry.get(record.getVerificationProvider());

            result = provider.verify(record.getTarget(), record.getProviderOutId(), cleanCode, record.getCodeHash());
        } catch (RuntimeException exception) {
            recordProviderFailure(record, "PROVIDER_EXCEPTION", null, "短信认证服务暂时不可用");
            return Decision.providerUnavailable("短信认证服务暂时不可用");
        }

        if (result == null || !result.isRequestSuccessful()) {
            recordProviderFailure(record, result == null ? null : result.getProviderCode(), result == null ? null : result.getProviderResult(), result == null ? "短信认证服务没有返回结果" : result.getMessage());

            return Decision.providerUnavailable(result == null || !StringUtils.hasText(result.getMessage()) ? "短信认证服务暂时不可用" : result.getMessage());
        }

        return finishAttempt(record, cleanCode, attempts + 1, maxAttempts, result.isVerified(), result.getProviderCode(), result.getProviderResult(), result.isVerified() ? null : result.getMessage());
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
        record.setVerifyTime(LocalDateTime.now());

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
}