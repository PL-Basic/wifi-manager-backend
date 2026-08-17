package com.plagod.ai.task;

import com.plagod.ai.model.AiModerationScene;

public class AiReviewTaskRequest {

    private String reviewRequestId;
    private String scene;
    private String businessType;
    private Long businessId;
    private Long tenantId;
    private Integer contentVersion;
    private String contentHash;

    public AiReviewTaskSubmission toSubmission() {
        if (contentVersion == null) {
            throw new IllegalArgumentException("contentVersion 不能为空");
        }
        return new AiReviewTaskSubmission(
                reviewRequestId,
                parseScene(),
                businessType,
                businessId,
                tenantId,
                contentVersion,
                contentHash);
    }

    private AiModerationScene parseScene() {
        if (scene == null || scene.trim().isEmpty()) {
            throw new IllegalArgumentException("scene 不能为空");
        }
        try {
            return AiModerationScene.valueOf(scene);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("scene 不受支持");
        }
    }

    public String getReviewRequestId() {
        return reviewRequestId;
    }

    public void setReviewRequestId(String reviewRequestId) {
        this.reviewRequestId = reviewRequestId;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(Integer contentVersion) {
        this.contentVersion = contentVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}
