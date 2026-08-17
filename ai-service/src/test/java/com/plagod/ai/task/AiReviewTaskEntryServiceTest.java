package com.plagod.ai.task;

import com.plagod.ai.model.AiModerationScene;
import com.plagod.entity.AiPolicyVersion;
import com.plagod.entity.AiReviewTask;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.AiPolicyVersionMapper;
import com.plagod.mapper.AiReviewTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewTaskEntryServiceTest {

    private AiReviewTaskMapper taskMapper;
    private AiPolicyVersionMapper policyVersionMapper;
    private RecordingTransactionManager transactionManager;
    private AiReviewTaskEntryService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(AiReviewTaskMapper.class);
        policyVersionMapper = mock(AiPolicyVersionMapper.class);
        transactionManager = new RecordingTransactionManager();
        service = new AiReviewTaskEntryService(
                taskMapper,
                policyVersionMapper,
                transactionManager);
        AiPolicyVersion active = new AiPolicyVersion();
        active.setPolicyVersionId(5L);
        when(policyVersionMapper.selectActiveByScene(
                "SUPPORT_SUBMISSION_REVIEW")).thenReturn(active);
    }

    @Test
    void createsFirstTaskInsideOneLocalTransaction() {
        when(taskMapper.selectByReviewRequestId("review-entry-001"))
                .thenReturn(null);
        doAnswer(invocation -> {
            AiReviewTask task = invocation.getArgument(0);
            task.setReviewTaskId(101L);
            return 1;
        }).when(taskMapper).insert(any(AiReviewTask.class));

        AiReviewTaskEntryResult result = service.submit(submission());

        assertFalse(result.isDuplicate());
        assertEquals(101L, result.getTask().getReviewTaskId());
        assertEquals("QUEUED", result.getTask().getTaskStatus());
        assertEquals(0, result.getTask().getAttemptCount());
        assertEquals(5L, result.getTask().getPolicyVersionId());
        assertEquals(1, transactionManager.getBegins());
        assertEquals(1, transactionManager.getCommits());
        assertEquals(0, transactionManager.getRollbacks());
    }

    @Test
    void replaysSameKeyAndFingerprintWithoutAnotherInsert() {
        AiReviewTask existing = existingTask();
        when(taskMapper.selectByReviewRequestId("review-entry-001"))
                .thenReturn(existing);

        AiReviewTaskEntryResult result = service.submit(submission());

        assertTrue(result.isDuplicate());
        assertSame(existing, result.getTask());
        assertEquals(5L, result.getTask().getPolicyVersionId());
        verify(taskMapper, never()).insert(any(AiReviewTask.class));
        verify(policyVersionMapper, never())
                .selectActiveByScene(any());
    }

    @Test
    void rejectsSameKeyWithDifferentFingerprintAs409() {
        AiReviewTask existing = existingTask();
        existing.setContentVersion(2);
        when(taskMapper.selectByReviewRequestId("review-entry-001"))
                .thenReturn(existing);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.submit(submission()));

        assertEquals(409, exception.getHttpStatus());
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", exception.getErrorKey());
        verify(taskMapper, never()).insert(any(AiReviewTask.class));
        verify(policyVersionMapper, never())
                .selectActiveByScene(any());
    }

    private AiReviewTaskSubmission submission() {
        return new AiReviewTaskSubmission(
                "review-entry-001",
                AiModerationScene.SUPPORT_SUBMISSION_REVIEW,
                "SUPPORT_SUBMISSION",
                71L,
                9L,
                1,
                repeat('a', 64));
    }

    private AiReviewTask existingTask() {
        AiReviewTask task = new AiReviewTask();
        task.setReviewTaskId(101L);
        task.setReviewRequestId("review-entry-001");
        task.setScene("SUPPORT_SUBMISSION_REVIEW");
        task.setBusinessType("SUPPORT_SUBMISSION");
        task.setBusinessId(71L);
        task.setTenantId(9L);
        task.setContentVersion(1);
        task.setContentHash(repeat('a', 64));
        task.setPolicyVersionId(5L);
        task.setTaskStatus("QUEUED");
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
