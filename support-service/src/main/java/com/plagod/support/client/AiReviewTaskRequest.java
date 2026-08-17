package com.plagod.support.client;

import com.plagod.support.SupportReviewRequest;

public final class AiReviewTaskRequest {

    private final String reviewRequestId;
    private final String scene;
    private final String businessType;
    private final Long businessId;
    private final Long tenantId;
    private final Integer contentVersion;
    private final String contentHash;

    private AiReviewTaskRequest(SupportReviewRequest request) {
        this.reviewRequestId = request.getReviewRequestId();
        this.scene = request.getScene();
        this.businessType = request.getBusinessType();
        this.businessId = request.getBusinessId();
        this.tenantId = request.getTenantId();
        this.contentVersion = request.getContentVersion();
        this.contentHash = request.getContentHash();
    }

    public static AiReviewTaskRequest from(SupportReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Support review 请求不能为空");
        }
        return new AiReviewTaskRequest(request);
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
}
