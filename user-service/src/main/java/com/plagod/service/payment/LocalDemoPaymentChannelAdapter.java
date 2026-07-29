package com.plagod.service.payment;

import com.plagod.configuration.PaymentProperties;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.LocalDemoPaymentCallbackRequest;
import com.plagod.dto.entitlement.VerifiedPaymentCallback;
import com.plagod.entity.entitlement.PaymentRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
public class LocalDemoPaymentChannelAdapter implements PaymentChannelAdapter {

    @Autowired
    private PaymentProperties properties;

    @Override
    public String channel() {
        return EntitlementTradeConstants.CHANNEL_LOCAL_DEMO;
    }

    @Override
    public PaymentChannelAction initiate(PaymentRecord payment) {
        return new PaymentChannelAction("LOCAL_DEMO_COMPLETE", "/entitlements/payments/" + payment.getPaymentNo() + "/demo-complete");
    }

    public LocalDemoPaymentCallbackRequest buildSuccessCallback(PaymentRecord payment) {

        LocalDemoPaymentCallbackRequest request = new LocalDemoPaymentCallbackRequest();

        /*
         * 同一支付单必须稳定生成同一个渠道交易号。
         * 这样并发点击和网络重试都会进入重复回调幂等分支。
         */
        String transactionNo = StringUtils.hasText(payment.getChannelTransactionNo()) ? payment.getChannelTransactionNo() : "LDT" + sha256(payment.getBusinessKey()).substring(0, 32).toUpperCase(Locale.ROOT);

        request.setBusinessKey(payment.getBusinessKey());
        request.setEventId("EVT" + randomText());
        request.setChannelTransactionNo(transactionNo);
        request.setPaidAmountCents(payment.getAmountCents());
        request.setTimestamp(Instant.now().getEpochSecond());
        request.setSignature(sign(canonical(request)));

        return request;
    }
    public VerifiedPaymentCallback verify(LocalDemoPaymentCallbackRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("支付回调不能为空");
        }

        String businessKey = requireText(request.getBusinessKey(), "支付业务键", 64);
        String eventId = requireText(request.getEventId(), "支付事件号", 64);
        String transactionNo = requireText(request.getChannelTransactionNo(), "渠道交易号", 64);

        if (request.getPaidAmountCents() == null || request.getPaidAmountCents() <= 0) {
            throw new IllegalArgumentException("支付金额无效");
        }

        long now = Instant.now().getEpochSecond();
        long timestamp = request.getTimestamp() == null ? 0L : request.getTimestamp();
        long window = properties.effectiveCallbackWindowSeconds();

        if (timestamp < now - window || timestamp > now + window) {
            throw new IllegalArgumentException("支付回调已经过期或时间无效");
        }

        request.setBusinessKey(businessKey);
        request.setEventId(eventId);
        request.setChannelTransactionNo(transactionNo);

        String canonicalPayload = canonical(request);
        String expectedSignature = sign(canonicalPayload);
        String actualSignature = request.getSignature() == null ? "" : request.getSignature().trim().toLowerCase(Locale.ROOT);

        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.US_ASCII), actualSignature.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("支付回调签名无效");
        }

        VerifiedPaymentCallback callback = new VerifiedPaymentCallback();
        callback.setChannel(channel());
        callback.setBusinessKey(businessKey);
        callback.setEventId(eventId);
        callback.setChannelTransactionNo(transactionNo);
        callback.setPaidAmountCents(request.getPaidAmountCents());
        callback.setPayloadHash(sha256(canonicalPayload));
        return callback;
    }

    private String canonical(LocalDemoPaymentCallbackRequest request) {
        return request.getBusinessKey() + "\n"
                + request.getEventId() + "\n"
                + request.getChannelTransactionNo() + "\n"
                + request.getPaidAmountCents() + "\n"
                + request.getTimestamp();
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.effectiveLocalDemoSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("支付签名算法不可用", exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("支付摘要算法不可用", exception);
        }
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

    private String randomText() {
        return UUID.randomUUID().toString().replace("-", "")
                .toUpperCase(Locale.ROOT);
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