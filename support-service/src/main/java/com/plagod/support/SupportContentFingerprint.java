package com.plagod.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class SupportContentFingerprint {

    private SupportContentFingerprint() {
    }

    static String requestFingerprint(String title, String contentText) {
        return sha256("SUPPORT_SUBMISSION_V1", title, contentText);
    }

    static String contentHash(String title, String contentText) {
        return sha256("SUPPORT_CONTENT_V1", title, contentText);
    }

    static String reviewRequestId(
            Long tenantId,
            Long userId,
            String clientRequestId,
            String requestFingerprint) {
        return "support_"
                + sha256(
                "SUPPORT_REVIEW_REQUEST_V1",
                String.valueOf(tenantId),
                String.valueOf(userId),
                clientRequestId,
                requestFingerprint);
    }

    private static String sha256(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length)
                        .getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(bytes);
                digest.update((byte) ';');
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            hex[index * 2] = digits[value >>> 4];
            hex[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(hex);
    }
}
