package com.plagod.support;

public final class SupportReviewRequest {

    private final String reviewRequestId;
    private final String scene;
    private final String businessType;
    private final Long businessId;
    private final Long tenantId;
    private final Integer contentVersion;
    private final String contentHash;
    private final String title;
    private final String contentText;

    public SupportReviewRequest(
            String reviewRequestId,
            String scene,
            String businessType,
            Long businessId,
            Long tenantId,
            Integer contentVersion,
            String contentHash,
            String title,
            String contentText) {
        this.reviewRequestId = reviewRequestId;
        this.scene = scene;
        this.businessType = businessType;
        this.businessId = businessId;
        this.tenantId = tenantId;
        this.contentVersion = contentVersion;
        this.contentHash = contentHash;
        this.title = title;
        this.contentText = contentText;
    }

    public String getReviewRequestId() {
        return reviewRequestId;
    }

    public String getScene() {
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

    public Integer getContentVersion() {
        return contentVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getTitle() {
        return title;
    }

    public String getContentText() {
        return contentText;
    }
}
