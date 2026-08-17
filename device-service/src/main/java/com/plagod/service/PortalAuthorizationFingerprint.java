package com.plagod.service;

import com.plagod.dto.device.PortalAuthorizeDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class PortalAuthorizationFingerprint {

    private PortalAuthorizationFingerprint() {
    }

    public static String calculate(
            Long tenantId,
            Long userId,
            PortalAuthorizeDTO request) {
        if (tenantId == null || userId == null || request == null) {
            throw new IllegalArgumentException("Portal 授权 fingerprint 参数不完整");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }

        append(digest, String.valueOf(tenantId));
        append(digest, String.valueOf(userId));
        append(digest, normalizeRequired(request.getDeviceCode()));
        append(digest, normalizeRequired(request.getMac()).toUpperCase(Locale.ROOT));
        append(digest, normalizeRequired(request.getIp()));
        append(digest, normalizeNullable(request.getDeviceInfo()));
        append(digest, String.valueOf(Boolean.TRUE.equals(request.getForceReplaceOldest())));

        byte[] hash = digest.digest();
        StringBuilder fingerprint = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            fingerprint.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return fingerprint.toString();
    }

    private static void append(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String normalizeRequired(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Portal 授权 fingerprint 缺少必填字段");
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim();
    }
}
