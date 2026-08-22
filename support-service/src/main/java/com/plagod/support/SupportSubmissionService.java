package com.plagod.support;

import com.plagod.entity.SupportContentReviewOutbox;
import com.plagod.entity.SupportSubmission;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.SupportContentReviewOutboxMapper;
import com.plagod.mapper.SupportSubmissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class SupportSubmissionService {

    static final String REVIEW_PENDING = "REVIEW_PENDING";
    static final String OUTBOX_PENDING = "PENDING";
    static final String SUPPORT_REVIEW_SCENE = "SUPPORT_SUBMISSION_REVIEW";
    static final String SUPPORT_BUSINESS_TYPE = "SUPPORT_SUBMISSION";

    private final SupportSubmissionMapper submissionMapper;
    private final SupportContentReviewOutboxMapper outboxMapper;
    private final Clock clock;

    @Autowired
    public SupportSubmissionService(
            SupportSubmissionMapper submissionMapper,
            SupportContentReviewOutboxMapper outboxMapper,
            Clock clock) {
        this.submissionMapper = submissionMapper;
        this.outboxMapper = outboxMapper;
        this.clock = clock;
    }

    @Transactional
    public SupportSubmissionResult submit(SupportSubmissionRequest request) {
        String fingerprint = SupportContentFingerprint.requestFingerprint(
                request.getTitle(),
                request.getContentText());
        SupportSubmission existing = findExisting(request);
        if (existing != null) {
            return replay(existing, fingerprint);
        }

        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                StableUnits.ASIA_SHANGHAI);
        SupportSubmission submission = new SupportSubmission();
        submission.setTenantId(request.getTenantId());
        submission.setUserId(request.getUserId());
        submission.setClientRequestId(request.getClientRequestId());
        submission.setRequestFingerprint(fingerprint);
        submission.setTitle(request.getTitle());
        submission.setContentText(request.getContentText());
        submission.setContentHash(SupportContentFingerprint.contentHash(
                request.getTitle(),
                request.getContentText()));
        submission.setContentVersion(1);
        submission.setAcceptedTime(now);
        submission.setQuotaDate(LocalDate.from(now));
        submission.setStatus(REVIEW_PENDING);
        submission.setReviewRequestId(SupportContentFingerprint.reviewRequestId(
                request.getTenantId(),
                request.getUserId(),
                request.getClientRequestId(),
                fingerprint));
        submission.setLimitRefunded(0);
        submission.setVersion(0);

        try {
            if (submissionMapper.insert(submission) != 1
                    || submission.getSubmissionId() == null) {
                throw new IllegalStateException("Support submission 写入失败");
            }
        } catch (DuplicateKeyException duplicateKeyException) {
            SupportSubmission concurrent =
                    submissionMapper.selectByRequestKeyForUpdate(
                            request.getTenantId(),
                            request.getUserId(),
                            request.getClientRequestId());
            if (concurrent == null) {
                throw duplicateKeyException;
            }
            return replay(concurrent, fingerprint);
        }

        SupportContentReviewOutbox outbox = new SupportContentReviewOutbox();
        outbox.setReviewRequestId(submission.getReviewRequestId());
        outbox.setScene(SUPPORT_REVIEW_SCENE);
        outbox.setBusinessType(SUPPORT_BUSINESS_TYPE);
        outbox.setBusinessId(submission.getSubmissionId());
        outbox.setTenantId(submission.getTenantId());
        outbox.setContentVersion(submission.getContentVersion());
        outbox.setContentHash(submission.getContentHash());
        outbox.setStatus(OUTBOX_PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(now);
        outbox.setVersion(0);
        if (outboxMapper.insert(outbox) != 1) {
            throw new IllegalStateException("Support review Outbox 写入失败");
        }

        return toResult(submission, false);
    }

    private SupportSubmission findExisting(SupportSubmissionRequest request) {
        return submissionMapper.selectByRequestKey(
                request.getTenantId(),
                request.getUserId(),
                request.getClientRequestId());
    }

    private SupportSubmissionResult replay(
            SupportSubmission existing,
            String requestFingerprint) {
        if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw ApiStatusException.idempotencyConflict(
                    "clientRequestId 已用于不同的 Support submission");
        }
        return toResult(existing, true);
    }

    private static SupportSubmissionResult toResult(
            SupportSubmission submission,
            boolean duplicate) {
        return new SupportSubmissionResult(
                submission.getSubmissionId(),
                submission.getReviewRequestId(),
                submission.getStatus(),
                duplicate);
    }
}
