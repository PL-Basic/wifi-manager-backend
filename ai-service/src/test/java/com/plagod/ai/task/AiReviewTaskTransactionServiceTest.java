package com.plagod.ai.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.entity.AiReviewTask;
import com.plagod.mapper.AiReviewTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewTaskTransactionServiceTest {

    private AiReviewTaskMapper taskMapper;
    private RecordingTransactionManager transactionManager;
    private AiReviewTaskTransactionService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(AiReviewTaskMapper.class);
        transactionManager = new RecordingTransactionManager();
        service = new AiReviewTaskTransactionService(
                taskMapper,
                transactionManager,
                new ObjectMapper());
    }

    @Test
    void claimsC01RecoveredQueuedOrExpiredTaskWithConditionalUpdate() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        when(taskMapper.finalizeExhausted(
                31L,
                now,
                5,
                AiReviewTaskTransactionService.MAX_ATTEMPTS_ERROR_CODE))
                .thenReturn(0);
        when(taskMapper.claim(
                31L,
                "worker-new",
                now,
                now.plusSeconds(30),
                5))
                .thenReturn(1);
        when(taskMapper.selectById(31L))
                .thenReturn(runningTask(31L, "worker-new", 2));

        AiReviewTaskClaim claim = service.claim(
                31L,
                "worker-new",
                now,
                now.plusSeconds(30),
                5);

        assertEquals(31L, claim.getReviewTaskId());
        assertEquals(2, claim.getAttemptCount());
        assertEquals(1, transactionManager.getCommits());
    }

    @Test
    void refusesFinalizeWhenClaimVersionWasReplaced() {
        AiReviewTaskClaim claim = claim(31L, "worker-stale", 1);
        when(taskMapper.completeResult(
                eq(31L),
                eq("worker-stale"),
                eq(8),
                eq("MANUAL_REQUIRED"),
                eq("MANUAL"),
                eq(0),
                eq("[]"),
                eq("LOW_CONFIDENCE"),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.completeResult(
                        claim,
                        AiModerationResult.manual("LOW_CONFIDENCE"),
                        LocalDateTime.of(2026, 8, 17, 10, 1)));
        assertEquals(1, transactionManager.getRollbacks());
    }

    @Test
    void movesExhaustedTaskToTerminalBeforeAnotherProviderCall() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        when(taskMapper.finalizeExhausted(
                31L,
                now,
                5,
                AiReviewTaskTransactionService.MAX_ATTEMPTS_ERROR_CODE))
                .thenReturn(1);

        AiReviewTaskClaim claim = service.claim(
                31L,
                "worker-new",
                now,
                now.plusSeconds(30),
                5);

        assertNull(claim);
        verify(taskMapper, never()).claim(
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void finalFailureAtAttemptLimitBecomesManualRequired() {
        AiReviewTaskClaim claim = claim(31L, "worker-final", 5);
        when(taskMapper.completeFailure(
                eq(31L),
                eq("worker-final"),
                eq(8),
                eq("AI_PROVIDER_UNAVAILABLE"),
                any(),
                any(),
                eq(5)))
                .thenReturn(1);

        AiReviewTaskDispatchOutcome outcome = service.completeFailure(
                claim,
                "AI_PROVIDER_UNAVAILABLE",
                LocalDateTime.of(2026, 8, 17, 10, 1),
                LocalDateTime.of(2026, 8, 17, 10, 2),
                5);

        assertEquals(
                AiReviewTaskDispatchOutcome.MANUAL_REQUIRED,
                outcome);
    }

    private AiReviewTaskClaim claim(
            Long taskId,
            String workerId,
            int attemptCount) {
        return AiReviewTaskClaim.from(
                runningTask(taskId, workerId, attemptCount),
                workerId);
    }

    private AiReviewTask runningTask(
            Long taskId,
            String workerId,
            int attemptCount) {
        AiReviewTask task = new AiReviewTask();
        task.setReviewTaskId(taskId);
        task.setReviewRequestId("review-transaction-001");
        task.setScene("ANNOUNCEMENT_REVIEW");
        task.setBusinessType("ANNOUNCEMENT");
        task.setBusinessId(501L);
        task.setContentVersion(3);
        task.setContentHash(repeat('b', 64));
        task.setPolicyVersionId(7L);
        task.setWorkerId(workerId);
        task.setAttemptCount(attemptCount);
        task.setTaskStatus("RUNNING");
        task.setVersion(8);
        return task;
    }

    private String repeat(char value, int count) {
        StringBuilder output = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
