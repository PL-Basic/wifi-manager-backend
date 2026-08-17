package com.plagod.support;

import com.plagod.entity.SupportContentReviewOutbox;
import com.plagod.exception.ApiErrorKey;
import com.plagod.mapper.SupportContentReviewOutboxMapper;
import com.plagod.mapper.SupportSubmissionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportReviewDispatcherTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-17T09:00:00Z"),
            StableUnits.ASIA_SHANGHAI);

    @Test
    void callsAiOutsideClaimTransactionThenFinalizesInNewTransaction() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        AtomicBoolean aiCalledOutsideTransaction = new AtomicBoolean();
        AtomicReference<LocalDateTime> claimTime = new AtomicReference<>();
        AtomicReference<LocalDateTime> leaseUntil = new AtomicReference<>();
        when(outboxMapper.claim(any(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    claimTime.set(invocation.getArgument(2));
                    leaseUntil.set(invocation.getArgument(3));
                    return 1;
                });
        when(outboxMapper.selectById(31L))
                .thenReturn(
                        SupportReviewOutboxTransactionServiceTest
                                .processingOutbox());
        when(submissionMapper.selectById(41L))
                .thenReturn(
                        SupportReviewOutboxTransactionServiceTest.submission());
        when(outboxMapper.markSent(any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return 1;
                });
        when(submissionMapper.attachAiReviewTask(
                41L,
                "review-41",
                501L,
                0)).thenReturn(1);
        SupportReviewPort port = request -> {
            aiCalledOutsideTransaction.set(!TransactionSynchronizationManager
                    .isActualTransactionActive());
            return new SupportReviewReceipt(501L);
        };
        TransactionTestSupport.RecordingTransactionManager manager =
                new TransactionTestSupport.RecordingTransactionManager();
        SupportReviewDispatcher dispatcher = dispatcher(
                outboxMapper,
                submissionMapper,
                port,
                manager);

        assertEquals(
                SupportReviewDispatchResult.SENT,
                dispatcher.dispatch(31L, "worker-a"));
        assertTrue(aiCalledOutsideTransaction.get());
        assertEquals(
                LocalDateTime.of(2026, 8, 17, 17, 0),
                claimTime.get());
        assertEquals(
                LocalDateTime.of(2026, 8, 17, 17, 0, 30),
                leaseUntil.get());
        assertEquals(2, manager.getBeginCount());
        assertEquals(2, manager.getCommitCount());
        assertEquals(0, manager.getRollbackCount());
    }

    @Test
    void aiCallSuspendsAnOuterCallingTransaction() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        prepareClaim(outboxMapper, submissionMapper);
        when(outboxMapper.selectById(31L)).thenReturn(
                SupportReviewOutboxTransactionServiceTest
                        .processingOutbox());
        when(outboxMapper.markSent(any(), anyString(), any()))
                .thenReturn(1);
        when(submissionMapper.attachAiReviewTask(
                41L,
                "review-41",
                501L,
                0)).thenReturn(1);
        AtomicBoolean aiCalledOutsideTransaction = new AtomicBoolean();
        SupportReviewPort port = request -> {
            aiCalledOutsideTransaction.set(!TransactionSynchronizationManager
                    .isActualTransactionActive());
            return new SupportReviewReceipt(501L);
        };
        TransactionTestSupport.RecordingTransactionManager manager =
                new TransactionTestSupport.RecordingTransactionManager();
        SupportReviewDispatcher dispatcher = dispatcher(
                outboxMapper,
                submissionMapper,
                port,
                manager);

        new TransactionTemplate(manager).execute(status -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            assertEquals(
                    SupportReviewDispatchResult.SENT,
                    dispatcher.dispatch(31L, "worker-a"));
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return null;
        });

        assertTrue(aiCalledOutsideTransaction.get());
        assertEquals(3, manager.getBeginCount());
        assertEquals(3, manager.getCommitCount());
    }

    @Test
    void retryableFailureReleasesClaimForBoundedRetry() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        prepareClaim(outboxMapper, submissionMapper);
        SupportContentReviewOutbox pending =
                SupportReviewOutboxTransactionServiceTest.processingOutbox();
        pending.setStatus("PENDING");
        pending.setWorkerId(null);
        pending.setLeaseUntil(null);
        pending.setRetryCount(1);
        when(outboxMapper.selectById(31L))
                .thenReturn(
                        SupportReviewOutboxTransactionServiceTest
                                .processingOutbox(),
                        pending);
        when(outboxMapper.releaseAfterFailure(
                eq(31L),
                eq("worker-a"),
                any(),
                any(),
                eq(3),
                eq(ApiErrorKey.AI_PROVIDER_UNAVAILABLE.value()),
                eq("AI_REVIEW_MANUAL_REQUIRED"))).thenReturn(1);
        SupportReviewPort port = request -> {
            assertFalse(TransactionSynchronizationManager
                    .isActualTransactionActive());
            throw new SupportReviewPortException(
                    ApiErrorKey.AI_PROVIDER_UNAVAILABLE.value());
        };
        TransactionTestSupport.RecordingTransactionManager manager =
                new TransactionTestSupport.RecordingTransactionManager();

        assertEquals(
                SupportReviewDispatchResult.RETRY_SCHEDULED,
                dispatcher(
                        outboxMapper,
                        submissionMapper,
                        port,
                        manager).dispatch(31L, "worker-a"));
        assertEquals(2, manager.getBeginCount());
        assertEquals(2, manager.getCommitCount());
    }

    @Test
    void maximumRetryFreezesManualStateAndCannotCallAiAgain() {
        SupportContentReviewOutboxMapper outboxMapper =
                mock(SupportContentReviewOutboxMapper.class);
        SupportSubmissionMapper submissionMapper =
                mock(SupportSubmissionMapper.class);
        prepareClaim(outboxMapper, submissionMapper);
        SupportContentReviewOutbox terminalClaim =
                SupportReviewOutboxTransactionServiceTest.processingOutbox();
        terminalClaim.setRetryCount(2);
        when(outboxMapper.selectById(31L)).thenReturn(terminalClaim);
        when(outboxMapper.releaseAfterFailure(
                eq(31L),
                eq("worker-a"),
                any(),
                any(),
                eq(3),
                anyString(),
                eq("AI_REVIEW_MANUAL_REQUIRED"))).thenReturn(1);
        when(submissionMapper.moveToManualReview(
                41L,
                "review-41",
                0,
                "AI_REVIEW_MANUAL_REQUIRED")).thenReturn(1);
        SupportReviewPort port = mock(SupportReviewPort.class);
        when(port.submit(any())).thenThrow(
                new SupportReviewPortException(
                        ApiErrorKey.AI_PROVIDER_UNAVAILABLE.value()));
        TransactionTestSupport.RecordingTransactionManager manager =
                new TransactionTestSupport.RecordingTransactionManager();
        SupportReviewDispatcher dispatcher = dispatcher(
                outboxMapper,
                submissionMapper,
                port,
                manager);

        assertEquals(
                SupportReviewDispatchResult.MANUAL_REQUIRED,
                dispatcher.dispatch(31L, "worker-a"));

        when(outboxMapper.claim(any(), anyString(), any(), any()))
                .thenReturn(0);
        assertEquals(
                SupportReviewDispatchResult.NOT_CLAIMED,
                dispatcher.dispatch(31L, "worker-b"));
        verify(port, times(1)).submit(any());
    }

    private static void prepareClaim(
            SupportContentReviewOutboxMapper outboxMapper,
            SupportSubmissionMapper submissionMapper) {
        when(outboxMapper.claim(any(), anyString(), any(), any()))
                .thenReturn(1);
        when(submissionMapper.selectById(41L))
                .thenReturn(
                        SupportReviewOutboxTransactionServiceTest.submission());
    }

    private static SupportReviewDispatcher dispatcher(
            SupportContentReviewOutboxMapper outboxMapper,
            SupportSubmissionMapper submissionMapper,
            SupportReviewPort port,
            TransactionTestSupport.RecordingTransactionManager manager) {
        SupportReviewOutboxTransactionService transactionService =
                TransactionTestSupport.proxy(
                        new SupportReviewOutboxTransactionService(
                                outboxMapper,
                                submissionMapper),
                        manager);
        SupportReviewRemoteGateway remoteGateway =
                new SupportReviewRemoteGateway(port, manager);
        return new SupportReviewDispatcher(
                transactionService,
                remoteGateway,
                FIXED_CLOCK,
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                3);
    }
}
