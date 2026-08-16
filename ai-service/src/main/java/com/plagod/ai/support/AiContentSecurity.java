package com.plagod.ai.support;

import com.plagod.ai.model.AiModerationRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AiContentSecurity {

    public static final String NORMALIZATION_VERSION = "wifi-ai-content-v1";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![\\w.+-])[\\w.+-]{1,64}@[a-z0-9.-]{1,190}\\.[a-z]{2,24}");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{8,}\\."
                    + "[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])");
    private static final Pattern INTERNAL_ID_PATTERN = Pattern.compile(
            "(?i)\\b(tenantId|userId|deviceCode)\\s*[:=]\\s*"
                    + "[A-Za-z0-9_-]{1,96}");

    public String contentHash(String title, String body) {
        String normalizedTitle = normalize(title);
        String normalizedBody = normalize(body);
        String framed = NORMALIZATION_VERSION
                + "\n"
                + normalizedTitle.length()
                + "\n"
                + normalizedTitle
                + "\n"
                + normalizedBody.length()
                + "\n"
                + normalizedBody;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(framed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    public boolean hashMatches(AiModerationRequest request) {
        String actual = contentHash(request.getTitle(), request.getBody());
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.US_ASCII),
                request.getContentHash().getBytes(StandardCharsets.US_ASCII));
    }

    public AiModerationRequest redactForOutbound(AiModerationRequest request) {
        return request.withContent(
                redact(request.getTitle()),
                redact(request.getBody()));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lineNormalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return Normalizer.normalize(lineNormalized, Normalizer.Form.NFC).trim();
    }

    private String redact(String value) {
        if (value == null) {
            return null;
        }
        String output = EMAIL_PATTERN.matcher(value).replaceAll("[EMAIL]");
        output = PHONE_PATTERN.matcher(output).replaceAll("[PHONE]");
        output = BEARER_PATTERN.matcher(output).replaceAll("Bearer [TOKEN]");
        output = JWT_PATTERN.matcher(output).replaceAll("[TOKEN]");
        return INTERNAL_ID_PATTERN.matcher(output).replaceAll("$1=[INTERNAL_ID]");
    }
}
