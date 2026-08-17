package com.plagod.support;

import java.util.Objects;
import java.util.regex.Pattern;

public final class SupportSubmissionRequest {

    private static final Pattern CLIENT_REQUEST_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");

    private final Long tenantId;
    private final Long userId;
    private final String clientRequestId;
    private final String title;
    private final String contentText;

    public SupportSubmissionRequest(
            Long tenantId,
            Long userId,
            String clientRequestId,
            String title,
            String contentText) {
        this.tenantId = requirePositive(tenantId, "tenantId");
        this.userId = requirePositive(userId, "userId");
        this.clientRequestId = requireClientRequestId(clientRequestId);
        this.title = requireText(title, 160, "title");
        this.contentText = requireText(contentText, 20000, "contentText");
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public String getTitle() {
        return title;
    }

    public String getContentText() {
        return contentText;
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
        return value;
    }

    private static String requireClientRequestId(String value) {
        Objects.requireNonNull(value, "clientRequestId");
        if (!CLIENT_REQUEST_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("clientRequestId 格式非法");
        }
        return value;
    }

    private static String requireText(String value, int maxLength, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 超过长度上限");
        }
        return normalized;
    }
}
