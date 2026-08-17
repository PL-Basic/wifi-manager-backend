package com.plagod.ai.task;

import com.plagod.ai.model.AiModerationRequest;
import com.plagod.entity.AiReviewTask;

import java.util.Objects;

public final class AiReviewTaskClaim {

    private final Long reviewTaskId;
    private final String reviewRequestId;
    private final String scene;
    private final String businessType;
    private final Long businessId;
    private final Long tenantId;
    private final int contentVersion;
    private final String contentHash;
    private final Long policyVersionId;
    private final String workerId;
    private final int attemptCount;
    private final int version;

    private AiReviewTaskClaim(
            Long reviewTaskId,
            String reviewRequestId,
            String scene,
            String businessType,
            Long businessId,
            Long tenantId,
            int contentVersion,
            String contentHash,
            Long policyVersionId,
            String workerId,
            int attemptCount,
            int version) {
        this.reviewTaskId = reviewTaskId;
        this.reviewRequestId = reviewRequestId;
        this.scene = scene;
        this.businessType = businessType;
        this.businessId = businessId;
        this.tenantId = tenantId;
        this.contentVersion = contentVersion;
        this.contentHash = contentHash;
        this.policyVersionId = policyVersionId;
        this.workerId = workerId;
        this.attemptCount = attemptCount;
        this.version = version;
    }

    public static AiReviewTaskClaim from(
            AiReviewTask task,
            String workerId) {
        Objects.requireNonNull(task, "task 不能为空");
        if (task.getReviewTaskId() == null
                || task.getAttemptCount() == null
                || task.getContentVersion() == null
                || task.getPolicyVersionId() == null
                || task.getVersion() == null) {
            throw new IllegalStateException("claim 后任务快照不完整");
        }
        return new AiReviewTaskClaim(
                task.getReviewTaskId(),
                task.getReviewRequestId(),
                task.getScene(),
                task.getBusinessType(),
                task.getBusinessId(),
                task.getTenantId(),
                task.getContentVersion(),
                task.getContentHash(),
                task.getPolicyVersionId(),
                workerId,
                task.getAttemptCount(),
                task.getVersion());
    }

    public boolean matches(AiModerationRequest request) {
        return request != null
                && reviewRequestId.equals(request.getReviewRequestId())
                && scene.equals(request.getScene().name())
                && contentHash.equals(request.getContentHash());
    }

    public Long getReviewTaskId() {
        return reviewTaskId;
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

    public int getContentVersion() {
        return contentVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Long getPolicyVersionId() {
        return policyVersionId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getVersion() {
        return version;
    }
}
