package com.plagod.support;

import com.plagod.entity.SupportContentReviewOutbox;
import com.plagod.entity.SupportSubmission;
import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.SupportContentReviewOutboxMapper;
import com.plagod.mapper.SupportSubmissionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportSubmissionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-17T01:02:03Z"),
            StableUnits.ASIA_SHANGHAI);

    @Test
    void replaysSameFingerprintAndRejectsConflictingPayload() {
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        AtomicReference<SupportSubmission> stored = new AtomicReference<>();
        when(submissionMapper.selectByRequestKey(
                anyLong(), anyLong(), anyString()))
                .thenAnswer(invocation -> stored.get());
        when(submissionMapper.insert(any(SupportSubmission.class)))
                .thenAnswer(invocation -> {
                    SupportSubmission submission = invocation.getArgument(0);
                    submission.setSubmissionId(101L);
                    stored.set(submission);
                    return 1;
                });
        when(outboxMapper.insert(any(SupportContentReviewOutbox.class)))
                .thenReturn(1);
        SupportSubmissionService service = new SupportSubmissionService(
                submissionMapper,
                outboxMapper,
                FIXED_CLOCK);

        SupportSubmissionRequest request = request("body-v1");
        SupportSubmissionResult first = service.submit(request);
        SupportSubmissionResult replay = service.submit(request);

        assertFalse(first.isDuplicate());
        assertTrue(replay.isDuplicate());
        assertEquals(first.getSubmissionId(), replay.getSubmissionId());
        assertEquals(first.getReviewRequestId(), replay.getReviewRequestId());
        verify(submissionMapper, times(1)).insert(any(SupportSubmission.class));
        verify(outboxMapper, times(1))
                .insert(any(SupportContentReviewOutbox.class));

        ApiStatusException conflict = assertThrows(
                ApiStatusException.class,
                () -> service.submit(request("body-v2")));
        assertEquals(409, conflict.getHttpStatus());
        assertEquals(
                ApiErrorKey.IDEMPOTENCY_KEY_CONFLICT.value(),
                conflict.getErrorKey());
        verify(submissionMapper, times(1)).insert(any(SupportSubmission.class));
        verify(outboxMapper, times(1))
                .insert(any(SupportContentReviewOutbox.class));
    }

    @Test
    void writesSubmissionAndOutboxInsideOneLocalTransaction() {
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        AtomicReference<SupportSubmission> insertedSubmission =
                new AtomicReference<>();
        when(submissionMapper.insert(any(SupportSubmission.class)))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    SupportSubmission submission = invocation.getArgument(0);
                    submission.setSubmissionId(202L);
                    insertedSubmission.set(submission);
                    return 1;
                });
        when(outboxMapper.insert(any(SupportContentReviewOutbox.class)))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return 1;
                });
        TransactionTestSupport.RecordingTransactionManager manager =
                new TransactionTestSupport.RecordingTransactionManager();
        SupportSubmissionService service = TransactionTestSupport.proxy(
                new SupportSubmissionService(
                        submissionMapper,
                        outboxMapper,
                        FIXED_CLOCK),
                manager);

        SupportSubmissionResult result = service.submit(request("body"));

        assertEquals(1, manager.getBeginCount());
        assertEquals(1, manager.getCommitCount());
        assertEquals(0, manager.getRollbackCount());
        assertFalse(result.isDuplicate());
        assertEquals(
                LocalDateTime.of(2026, 8, 17, 9, 2, 3),
                insertedSubmission.get().getAcceptedTime());
        assertEquals(
                LocalDate.of(2026, 8, 17),
                insertedSubmission.get().getQuotaDate());

        ArgumentCaptor<SupportContentReviewOutbox> captor =
                ArgumentCaptor.forClass(SupportContentReviewOutbox.class);
        verify(outboxMapper).insert(captor.capture());
        SupportContentReviewOutbox outbox = captor.getValue();
        assertEquals(202L, outbox.getBusinessId());
        assertEquals("PENDING", outbox.getStatus());
        assertEquals(0, outbox.getRetryCount());
        assertEquals(
                LocalDateTime.of(2026, 8, 17, 9, 2, 3),
                outbox.getNextRetryTime());
        assertNotNull(outbox.getReviewRequestId());
    }

    @Test
    void duplicateInsertUsesLockingReadForConcurrentReplay() {
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmission stored = new SupportSubmission();
        stored.setSubmissionId(303L);
        stored.setReviewRequestId("review-303");
        stored.setStatus("REVIEW_PENDING");
        stored.setRequestFingerprint(
                SupportContentFingerprint.requestFingerprint(
                        "Need help",
                        "body"));
        when(submissionMapper.insert(any(SupportSubmission.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(submissionMapper.selectByRequestKeyForUpdate(
                11L,
                22L,
                "support-request-1")).thenReturn(stored);
        SupportSubmissionService service = new SupportSubmissionService(
                submissionMapper,
                outboxMapper,
                FIXED_CLOCK);

        SupportSubmissionResult replay = service.submit(request("body"));

        assertTrue(replay.isDuplicate());
        assertEquals(303L, replay.getSubmissionId());
        verify(submissionMapper).selectByRequestKeyForUpdate(
                11L,
                22L,
                "support-request-1");
        verify(outboxMapper, never())
                .insert(any(SupportContentReviewOutbox.class));
    }

    private static SupportSubmissionRequest request(String content) {
        return new SupportSubmissionRequest(
                11L,
                22L,
                "support-request-1",
                "Need help",
                content);
    }
}
