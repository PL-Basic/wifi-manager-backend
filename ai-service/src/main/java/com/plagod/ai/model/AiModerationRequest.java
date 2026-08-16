package com.plagod.ai.model;

import java.util.Objects;
import java.util.regex.Pattern;

public final class AiModerationRequest {

    private static final Pattern HASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern REVIEW_REQUEST_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{8,96}$");
    private static final int MAX_REVIEW_REQUEST_ID_LENGTH = 96;
    private static final int MAX_POLICY_VERSION_LENGTH = 96;
    private static final int MAX_LANGUAGE_LENGTH = 24;
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_BODY_LENGTH = 20000;

    private final AiModerationScene scene;
    private final String reviewRequestId;
    private final String policyVersion;
    private final String title;
    private final String body;
    private final String contentHash;
    private final String language;

    public AiModerationRequest(
            AiModerationScene scene,
            String reviewRequestId,
            String policyVersion,
            String title,
            String body,
            String contentHash,
            String language) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.reviewRequestId = requireText(
                reviewRequestId,
                MAX_REVIEW_REQUEST_ID_LENGTH,
                "reviewRequestId");
        if (!REVIEW_REQUEST_ID_PATTERN.matcher(this.reviewRequestId).matches()) {
            throw new IllegalArgumentException("reviewRequestId 格式非法");
        }
        this.policyVersion = requireText(
                policyVersion,
                MAX_POLICY_VERSION_LENGTH,
                "policyVersion");
        this.title = optionalText(title, MAX_TITLE_LENGTH, "title");
        this.body = optionalText(body, MAX_BODY_LENGTH, "body");
        if (this.title == null && this.body == null) {
            throw new IllegalArgumentException("title 和 body 不能同时为空");
        }
        if (contentHash == null || !HASH_PATTERN.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash 必须是小写 SHA-256");
        }
        this.contentHash = contentHash;
        this.language = requireText(language, MAX_LANGUAGE_LENGTH, "language");
    }

    public AiModerationScene getScene() {
        return scene;
    }

    public String getReviewRequestId() {
        return reviewRequestId;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getLanguage() {
        return language;
    }

    public AiModerationRequest withContent(String outboundTitle, String outboundBody) {
        return new AiModerationRequest(
                scene,
                reviewRequestId,
                policyVersion,
                outboundTitle,
                outboundBody,
                contentHash,
                language);
    }

    private static String requireText(String value, int maxLength, String field) {
        String checked = optionalText(value, maxLength, field);
        if (checked == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return checked;
    }

    private static String optionalText(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String checked = value.trim();
        if (checked.isEmpty()) {
            return null;
        }
        if (checked.length() > maxLength) {
            throw new IllegalArgumentException(field + " 超过长度上限");
        }
        return checked;
    }
}
