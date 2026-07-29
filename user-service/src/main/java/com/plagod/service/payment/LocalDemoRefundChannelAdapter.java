package com.plagod.service.payment;

import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.LocalDemoRefundResultRequest;
import com.plagod.dto.entitlement.VerifiedRefundResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Locale;

@Component
public class LocalDemoRefundChannelAdapter {

    public VerifiedRefundResult build(String rawRefundNo, LocalDemoRefundResultRequest request) {

        String refundNo = requireText(rawRefundNo, "退款单号", 64).toUpperCase(Locale.ROOT);

        if (request == null || request.getSuccess() == null) {
            throw new IllegalArgumentException("Demo退款结果不能为空");
        }

        String requestId = requireText(request.getRequestId(), "结果请求号", 56);

        String failureMessage = StringUtils.hasText(request.getFailureMessage()) ? requireText(request.getFailureMessage(), "失败原因", 255) : null;

        if (!request.getSuccess() && !StringUtils.hasText(failureMessage)) {
            failureMessage = "本地Demo渠道退款失败";
        }

        String canonical = refundNo + "\n" + requestId + "\n" + request.getSuccess() + "\n" + (failureMessage == null ? "" : failureMessage);

        VerifiedRefundResult result = new VerifiedRefundResult();
        result.setRefundNo(refundNo);
        result.setChannel(EntitlementTradeConstants.CHANNEL_LOCAL_DEMO);
        result.setEventId("RFE" + sha256(refundNo + "\n" + requestId).substring(0, 32).toUpperCase(Locale.ROOT));
        result.setChannelRefundNo("LDR" + sha256(refundNo).substring(0, 32).toUpperCase(Locale.ROOT));
        result.setSuccess(request.getSuccess());
        result.setPayloadHash(sha256(canonical));
        result.setFailureMessage(failureMessage);
        return result;
    }

    private String requireText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度无效");
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("退款摘要算法不可用", exception);
        }
    }

    private String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];

        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }
}