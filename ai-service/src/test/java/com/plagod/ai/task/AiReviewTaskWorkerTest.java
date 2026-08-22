package com.plagod.ai.task;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewTaskWorkerTest {

    @Test
    void scansAndContinuesAfterOneTaskFails() {
        AiReviewTaskTransactionService transactionService =
                mock(AiReviewTaskTransactionService.class);
        AiReviewTaskDispatcher dispatcher =
                mock(AiReviewTaskDispatcher.class);
        when(transactionService.findDispatchableIds(
                any(LocalDateTime.class),
                eq(20)))
                .thenReturn(Arrays.asList(11L, 12L, 13L));
        when(dispatcher.dispatchOne(11L, "worker-test"))
                .thenThrow(new IllegalStateException("expected"));

        AiReviewTaskWorker worker = new AiReviewTaskWorker(
                transactionService,
                dispatcher,
                20,
                "worker-test");
        worker.dispatchDueTasks();

        verify(dispatcher).dispatchOne(11L, "worker-test");
        verify(dispatcher).dispatchOne(12L, "worker-test");
        verify(dispatcher).dispatchOne(13L, "worker-test");
    }
}
