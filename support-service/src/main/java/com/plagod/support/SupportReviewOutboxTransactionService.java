package com.plagod.support;

import com.plagod.entity.SupportContentReviewOutbox;
import com.plagod.entity.SupportSubmission;
import com.plagod.exception.ApiErrorKey;
import com.plagod.mapper.SupportContentReviewOutboxMapper;
import com.plagod.mapper.SupportSubmissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class SupportReviewOutboxTransactionService {

    static final String OUTBOX_PROCESSING = "PROCESSING";
    static final String TERMINAL_ERROR_CODE = "AI_REVIEW_MANUAL_REQUIRED";

    private final SupportContentReviewOutboxMapper outboxMapper;
    private final SupportSubmissionMapper submissionMapper;

    public SupportReviewOutboxTransactionService(
            SupportContentReviewOutboxMapper outboxMapper,
            SupportSubmissionMapper submissionMapper) {
        this.outboxMapper = outboxMapper;
        this.submissionMapper = submissionMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> findDispatchableEventIds(
            LocalDateTime now,
            int limit) {
        Objects.requireNonNull(now, "扫描时间不能为空");
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("扫描批次必须在 1 到 500 之间");
        }
        List<Long> eventIds = outboxMapper.selectDispatchableEventIds(
                now,
                limit);
        if (eventIds == null) {
            throw new IllegalStateException("Support review 扫描未返回结果");
        }
        return eventIds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SupportReviewClaim claim(
            Long eventId,
            String workerId,
            LocalDateTime now,
            Duration leaseDuration) {
        requirePositive(eventId, "eventId");
        requireWorkerId(workerId);
        if (leaseDuration == null
                || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration 必须为正数");
        }
        LocalDateTime leaseUntil = now.plus(leaseDuration);
        if (outboxMapper.claim(eventId, workerId, now, leaseUntil) != 1) {
            return null;
        }

        SupportContentReviewOutbox outbox = outboxMapper.selectById(eventId);
        if (outbox == null
                || !OUTBOX_PROCESSING.equals(outbox.getStatus())
                || !workerId.equals(outbox.getWorkerId())
                || !SupportSubmissionService.SUPPORT_REVIEW_SCENE.equals(
                outbox.getScene())
                || !SupportSubmissionService.SUPPORT_BUSINESS_TYPE.equals(
                outbox.getBusinessType())) {
            throw new IllegalStateException("Support review Outbox claim 结果不一致");
        }
        SupportSubmission submission =
                submissionMapper.selectById(outbox.getBusinessId());
        validateSubmission(outbox, submission);

        SupportReviewRequest request = new SupportReviewRequest(
                outbox.getReviewRequestId(),
                outbox.getScene(),
                outbox.getBusinessType(),
                outbox.getBusinessId(),
                outbox.getTenantId(),
                outbox.getContentVersion(),
                outbox.getContentHash(),
                submission.getTitle(),
                submission.getContentText());
        return new SupportReviewClaim(
                eventId,
                workerId,
                outbox.getRetryCount(),
                submission.getVersion(),
                request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeSuccess(
            SupportReviewClaim claim,
            SupportReviewReceipt receipt,
            LocalDateTime now) {
        if (outboxMapper.markSent(
                claim.getEventId(),
                claim.getWorkerId(),
                now) != 1) {
            throw new SupportReviewOwnershipException();
        }

        SupportReviewRequest request = claim.getReviewRequest();
        int attached = submissionMapper.attachAiReviewTask(
                request.getBusinessId(),
                request.getReviewRequestId(),
                receipt.getReviewTaskId(),
                claim.getSubmissionVersion());
        if (attached == 1) {
            return;
        }
        SupportSubmission current =
                submissionMapper.selectById(request.getBusinessId());
        if (current == null
                || !receipt.getReviewTaskId().equals(current.getAiReviewTaskId())
                || !request.getReviewRequestId().equals(
                current.getReviewRequestId())) {
            throw new IllegalStateException("AI review task 结果无法关联到 submission");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SupportReviewDispatchResult finalizeFailure(
            SupportReviewClaim claim,
            LocalDateTime now,
            LocalDateTime nextRetryTime,
            int maxAttempts,
            String errorCode) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须为正数");
        }
        ApiErrorKey.requireValid(errorCode);
        boolean terminal = claim.getRetryCount() + 1 >= maxAttempts;
        if (outboxMapper.releaseAfterFailure(
                claim.getEventId(),
                claim.getWorkerId(),
                now,
                nextRetryTime,
                maxAttempts,
                errorCode,
                TERMINAL_ERROR_CODE) != 1) {
            throw new SupportReviewOwnershipException();
        }
        if (terminal && submissionMapper.moveToManualReview(
                claim.getReviewRequest().getBusinessId(),
                claim.getReviewRequest().getReviewRequestId(),
                claim.getSubmissionVersion(),
                TERMINAL_ERROR_CODE) != 1) {
            throw new IllegalStateException(
                    "Support submission 人工审核状态存在版本冲突");
        }
        return terminal
                ? SupportReviewDispatchResult.MANUAL_REQUIRED
                : SupportReviewDispatchResult.RETRY_SCHEDULED;
    }

    private static void validateSubmission(
            SupportContentReviewOutbox outbox,
            SupportSubmission submission) {
        if (submission == null
                || !outbox.getBusinessId().equals(submission.getSubmissionId())
                || !outbox.getTenantId().equals(submission.getTenantId())
                || !outbox.getReviewRequestId().equals(
                submission.getReviewRequestId())
                || !outbox.getContentVersion().equals(
                submission.getContentVersion())
                || !outbox.getContentHash().equals(
                submission.getContentHash())
                || !SupportSubmissionService.REVIEW_PENDING.equals(
                submission.getStatus())
                || submission.getVersion() == null
                || submission.getVersion() < 0
                || outbox.getRetryCount() == null
                || outbox.getRetryCount() < 0) {
            throw new IllegalStateException("Support review 引用与 submission 不一致");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
    }

    private static void requireWorkerId(String workerId) {
        if (workerId == null
                || workerId.trim().isEmpty()
                || workerId.length() > 64) {
            throw new IllegalArgumentException("workerId 格式非法");
        }
    }
}
