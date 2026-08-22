package com.plagod.ai.task;

import com.plagod.ai.model.AiModerationScene;

import java.util.Objects;
import java.util.regex.Pattern;

public final class AiReviewTaskSubmission {

    private static final Pattern REVIEW_REQUEST_ID =
            Pattern.compile("^[A-Za-z0-9_-]{8,96}$");
    private static final Pattern CONTENT_HASH =
            Pattern.compile("^[a-f0-9]{64}$");

    private final String reviewRequestId;
    private final AiModerationScene scene;
    private final String businessType;
    private final Long businessId;
    private final Long tenantId;
    private final int contentVersion;
    private final String contentHash;

    public AiReviewTaskSubmission(
            String reviewRequestId,
            AiModerationScene scene,
            String businessType,
            Long businessId,
            Long tenantId,
            int contentVersion,
            String contentHash) {
        if (reviewRequestId == null
                || !REVIEW_REQUEST_ID.matcher(reviewRequestId).matches()) {
            throw new IllegalArgumentException("reviewRequestId 格式非法");
        }
        this.scene = Objects.requireNonNull(scene, "scene 不能为空");
        this.businessType = requireBusinessType(scene, businessType, tenantId);
        this.businessId = requirePositive(businessId, "businessId");
        if (tenantId != null && tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须为正数");
        }
        if (contentVersion <= 0) {
            throw new IllegalArgumentException("contentVersion 必须为正数");
        }
        if (contentHash == null
                || !CONTENT_HASH.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash 必须是小写 SHA-256");
        }
        this.reviewRequestId = reviewRequestId;
        this.tenantId = tenantId;
        this.contentVersion = contentVersion;
        this.contentHash = contentHash;
    }

    public String getReviewRequestId() {
        return reviewRequestId;
    }

    public AiModerationScene getScene() {
        return scene;
    }

    public String getBusinessType() {
        return businessType;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public int getContentVersion() {
        return contentVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    private static String requireBusinessType(
            AiModerationScene scene,
            String businessType,
            Long tenantId) {
        if (scene == AiModerationScene.ANNOUNCEMENT_REVIEW
                && "ANNOUNCEMENT".equals(businessType)) {
            return businessType;
        }
        if (scene == AiModerationScene.SUPPORT_SUBMISSION_REVIEW
                && "SUPPORT_SUBMISSION".equals(businessType)
                && tenantId != null
                && tenantId > 0) {
            return businessType;
        }
        throw new IllegalArgumentException("scene 与 businessType/tenantId 不匹配");
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
        return value;
    }
}
