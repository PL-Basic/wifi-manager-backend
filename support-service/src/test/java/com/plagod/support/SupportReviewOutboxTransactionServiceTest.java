package com.plagod.support;

import com.plagod.entity.SupportContentReviewOutbox;
import com.plagod.entity.SupportSubmission;
import com.plagod.mapper.SupportContentReviewOutboxMapper;
import com.plagod.mapper.SupportSubmissionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportReviewOutboxTransactionServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 17, 9, 0);

    @Test
    void claimReturnsStableSubmissionPayloadAfterConditionalUpdate() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        when(outboxMapper.claim(
                31L,
                "worker-a",
                NOW,
                NOW.plusSeconds(30))).thenReturn(1);
        when(outboxMapper.selectById(31L)).thenReturn(processingOutbox());
        when(submissionMapper.selectById(41L)).thenReturn(submission());
        SupportReviewOutboxTransactionService service =
                new SupportReviewOutboxTransactionService(
                        outboxMapper,
                        submissionMapper);

        SupportReviewClaim claim = service.claim(
                31L,
                "worker-a",
                NOW,
                Duration.ofSeconds(30));

        assertNotNull(claim);
        assertEquals(31L, claim.getEventId());
        assertEquals("worker-a", claim.getWorkerId());
        assertEquals("review-41",
                claim.getReviewRequest().getReviewRequestId());
        assertEquals("question", claim.getReviewRequest().getContentText());
    }

    @Test
    void rejectedClaimDoesNotReadOrCallAiPayload() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        when(outboxMapper.claim(
                31L,
                "worker-a",
                NOW,
                NOW.plusSeconds(30))).thenReturn(0);
        SupportReviewOutboxTransactionService service =
                new SupportReviewOutboxTransactionService(
                        outboxMapper,
                        submissionMapper);

        assertNull(service.claim(
                31L,
                "worker-a",
                NOW,
                Duration.ofSeconds(30)));
        verify(outboxMapper, never()).selectById(any());
        verify(submissionMapper, never()).selectById(any());
    }

    @Test
    void wrongWorkerCannotFinalizeSuccessOrAttachResult() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        when(outboxMapper.markSent(31L, "stale-worker", NOW))
                .thenReturn(0);
        SupportReviewOutboxTransactionService service =
                new SupportReviewOutboxTransactionService(
                        outboxMapper,
                        submissionMapper);

        assertThrows(
                SupportReviewOwnershipException.class,
                () -> service.finalizeSuccess(
                        claim("stale-worker"),
                        new SupportReviewReceipt(501L),
                        NOW));
        verify(submissionMapper, never()).attachAiReviewTask(
                any(), anyString(), any(), any());
    }

    @Test
    void successfulOwnerFinalizesOutboxAndStoresAiTaskReference() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        when(outboxMapper.markSent(31L, "worker-a", NOW)).thenReturn(1);
        when(submissionMapper.attachAiReviewTask(
                41L,
                "review-41",
                501L,
                0)).thenReturn(1);
        SupportReviewOutboxTransactionService service =
                new SupportReviewOutboxTransactionService(
                        outboxMapper,
                        submissionMapper);

        service.finalizeSuccess(
                claim("worker-a"),
                new SupportReviewReceipt(501L),
                NOW);

        verify(outboxMapper).markSent(31L, "worker-a", NOW);
        verify(submissionMapper).attachAiReviewTask(
                41L,
                "review-41",
                501L,
                0);
    }

    @Test
    void wrongWorkerCannotReleaseOrAdvanceRetry() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        when(outboxMapper.releaseAfterFailure(
                eq(31L),
                eq("stale-worker"),
                eq(NOW),
                eq(NOW.plusSeconds(30)),
                eq(3),
                anyString(),
                anyString())).thenReturn(0);
        SupportReviewOutboxTransactionService service =
                new SupportReviewOutboxTransactionService(
                        outboxMapper,
                        submissionMapper);

        assertThrows(
                SupportReviewOwnershipException.class,
                () -> service.finalizeFailure(
                        claim("stale-worker"),
                        NOW,
                        NOW.plusSeconds(30),
                        3,
                        "AI_PROVIDER_UNAVAILABLE"));
        verify(outboxMapper, never()).selectById(any());
    }

    @Test
    void maximumRetryMovesSubmissionToManualInSameTransaction() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        when(outboxMapper.releaseAfterFailure(
                31L,
                "worker-a",
                NOW,
                NOW.plusSeconds(30),
                3,
                "AI_PROVIDER_UNAVAILABLE",
                "AI_REVIEW_MANUAL_REQUIRED")).thenReturn(1);
        when(submissionMapper.moveToManualReview(
                41L,
                "review-41",
                0,
                "AI_REVIEW_MANUAL_REQUIRED")).thenReturn(1);
        SupportReviewOutboxTransactionService service =
                new SupportReviewOutboxTransactionService(
                        outboxMapper,
                        submissionMapper);

        assertEquals(
                SupportReviewDispatchResult.MANUAL_REQUIRED,
                service.finalizeFailure(
                        claim("worker-a", 2),
                        NOW,
                        NOW.plusSeconds(30),
                        3,
                        "AI_PROVIDER_UNAVAILABLE"));
        verify(submissionMapper).moveToManualReview(
                41L,
                "review-41",
                0,
                "AI_REVIEW_MANUAL_REQUIRED");
    }

    @Test
    void manualVersionConflictRollsBackOutboxTerminalUpdate() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        when(outboxMapper.releaseAfterFailure(
                31L,
                "worker-a",
                NOW,
                NOW.plusSeconds(30),
                3,
                "AI_PROVIDER_UNAVAILABLE",
                "AI_REVIEW_MANUAL_REQUIRED")).thenReturn(1);
        when(submissionMapper.moveToManualReview(
                41L,
                "review-41",
                0,
                "AI_REVIEW_MANUAL_REQUIRED")).thenReturn(0);
        TransactionTestSupport.RecordingTransactionManager manager =
                new TransactionTestSupport.RecordingTransactionManager();
        SupportReviewOutboxTransactionService service =
                TransactionTestSupport.proxy(
                        new SupportReviewOutboxTransactionService(
                                outboxMapper,
                                submissionMapper),
                        manager);

        assertThrows(
                IllegalStateException.class,
                () -> service.finalizeFailure(
                        claim("worker-a", 2),
                        NOW,
                        NOW.plusSeconds(30),
                        3,
                        "AI_PROVIDER_UNAVAILABLE"));
        assertEquals(0, manager.getCommitCount());
        assertEquals(1, manager.getRollbackCount());
    }

    @Test
    void claimAndFinalizeAlwaysUseIndependentTransactions() {
        for (String methodName : new String[]{
                "claim",
                "finalizeSuccess",
                "finalizeFailure"}) {
            Transactional transactional = null;
            for (java.lang.reflect.Method method
                    : SupportReviewOutboxTransactionService.class
                    .getDeclaredMethods()) {
                if (methodName.equals(method.getName())) {
                    transactional = method.getAnnotation(Transactional.class);
                    break;
                }
            }
            assertNotNull(transactional);
            assertEquals(
                    Propagation.REQUIRES_NEW,
                    transactional.propagation());
        }
    }

    private static SupportReviewClaim claim(String workerId) {
        return claim(workerId, 0);
    }

    private static SupportReviewClaim claim(
            String workerId,
            int retryCount) {
        return new SupportReviewClaim(
                31L,
                workerId,
                retryCount,
                0,
                new SupportReviewRequest(
                        "review-41",
                        "SUPPORT_SUBMISSION_REVIEW",
                        "SUPPORT_SUBMISSION",
                        41L,
                        11L,
                        1,
                        hash(),
                        "title",
                        "question"));
    }

    static SupportContentReviewOutbox processingOutbox() {
        SupportContentReviewOutbox outbox = new SupportContentReviewOutbox();
        outbox.setEventId(31L);
        outbox.setReviewRequestId("review-41");
        outbox.setScene("SUPPORT_SUBMISSION_REVIEW");
        outbox.setBusinessType("SUPPORT_SUBMISSION");
        outbox.setBusinessId(41L);
        outbox.setTenantId(11L);
        outbox.setContentVersion(1);
        outbox.setContentHash(hash());
        outbox.setStatus("PROCESSING");
        outbox.setRetryCount(0);
        outbox.setWorkerId("worker-a");
        outbox.setLeaseUntil(NOW.plusSeconds(30));
        return outbox;
    }

    static SupportSubmission submission() {
        SupportSubmission submission = new SupportSubmission();
        submission.setSubmissionId(41L);
        submission.setTenantId(11L);
        submission.setReviewRequestId("review-41");
        submission.setContentVersion(1);
        submission.setContentHash(hash());
        submission.setTitle("title");
        submission.setContentText("question");
        submission.setStatus("REVIEW_PENDING");
        submission.setVersion(0);
        return submission;
    }

    static String hash() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }
}
