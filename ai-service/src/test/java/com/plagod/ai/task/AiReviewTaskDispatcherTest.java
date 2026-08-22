package com.plagod.ai.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.ai.model.AiModerationDecision;
import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiModerationScene;
import com.plagod.ai.model.AiProviderAvailability;
import com.plagod.ai.observability.AiProviderMetrics;
import com.plagod.ai.provider.AiModerationProvider;
import com.plagod.ai.provider.AiProviderException;
import com.plagod.ai.provider.AiProviderRegistry;
import com.plagod.ai.service.AiModerationService;
import com.plagod.ai.support.AiContentSecurity;
import com.plagod.entity.AiReviewTask;
import com.plagod.mapper.AiReviewTaskMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewTaskDispatcherTest {

    @Test
    void suspendsOuterTransactionForProviderThenResumesIt() {
        AtomicBoolean providerSawTransaction = new AtomicBoolean(true);
        AtomicBoolean outerTransactionResumed = new AtomicBoolean(false);
        AiModerationProvider provider = provider(
                providerSawTransaction,
                approvedResult());
        Fixture fixture = fixture(provider, 1, 5);
        when(fixture.taskMapper.completeResult(
                eq(41L),
                eq("worker-ai"),
                eq(8),
                eq("SUCCEEDED"),
                eq("APPROVE"),
                eq(9200),
                eq("[\"SAFE\"]"),
                eq("CONTENT_SAFE"),
                eq("provider-request-41"),
                eq("model-v1"),
                eq(24L),
                any()))
                .thenReturn(1);

        AiReviewTaskDispatchOutcome outcome =
                new TransactionTemplate(fixture.transactionManager)
                        .execute(status -> {
                            assertTrue(TransactionSynchronizationManager
                                    .isActualTransactionActive());
                            AiReviewTaskDispatchOutcome dispatched =
                                    fixture.dispatcher.dispatchOne(
                                            41L,
                                            "worker-ai");
                            outerTransactionResumed.set(
                                    TransactionSynchronizationManager
                                            .isActualTransactionActive());
                            return dispatched;
                        });

        assertEquals(AiReviewTaskDispatchOutcome.SUCCEEDED, outcome);
        assertFalse(providerSawTransaction.get());
        assertTrue(outerTransactionResumed.get());
        assertEquals(3, fixture.transactionManager.getBegins());
        assertEquals(3, fixture.transactionManager.getCommits());
        verify(fixture.inputLoader).load(any(AiReviewTaskClaim.class));
    }

    @Test
    void retriesSafeProviderFailureWithoutPersistingExceptionDetails() {
        AtomicBoolean providerSawTransaction = new AtomicBoolean(true);
        AiModerationProvider provider = failingProvider(
                providerSawTransaction);
        Fixture fixture = fixture(provider, 1, 5);
        when(fixture.taskMapper.completeFailure(
                eq(41L),
                eq("worker-ai"),
                eq(8),
                eq("AI_PROVIDER_UNAVAILABLE"),
                any(),
                any(),
                eq(5)))
                .thenReturn(1);

        AiReviewTaskDispatchOutcome outcome =
                fixture.dispatcher.dispatchOne(
                        41L,
                        "worker-ai");

        assertEquals(
                AiReviewTaskDispatchOutcome.RETRY_SCHEDULED,
                outcome);
        assertFalse(providerSawTransaction.get());
    }

    @Test
    void finalProviderFailureAtLimitBecomesManualRequired() {
        AtomicBoolean providerSawTransaction = new AtomicBoolean(true);
        Fixture fixture = fixture(
                failingProvider(providerSawTransaction),
                5,
                5);
        when(fixture.taskMapper.completeFailure(
                eq(41L),
                eq("worker-ai"),
                eq(8),
                eq("AI_PROVIDER_UNAVAILABLE"),
                any(),
                any(),
                eq(5)))
                .thenReturn(1);

        AiReviewTaskDispatchOutcome outcome =
                fixture.dispatcher.dispatchOne(
                        41L,
                        "worker-ai");

        assertEquals(
                AiReviewTaskDispatchOutcome.MANUAL_REQUIRED,
                outcome);
        assertFalse(providerSawTransaction.get());
    }

    @Test
    void freezesInputConflictAsManualWithoutCallingProvider() {
        AtomicBoolean providerSawTransaction = new AtomicBoolean(true);
        Fixture fixture = fixture(
                provider(providerSawTransaction, approvedResult()),
                1,
                5);
        doThrow(new AiReviewTaskInputException())
                .when(fixture.inputLoader)
                .load(any(AiReviewTaskClaim.class));
        when(fixture.taskMapper.completeResult(
                eq(41L),
                eq("worker-ai"),
                eq(8),
                eq("MANUAL_REQUIRED"),
                eq("MANUAL"),
                eq(0),
                eq("[]"),
                eq("AI_TASK_INPUT_CONFLICT"),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(1);

        AiReviewTaskDispatchOutcome outcome =
                fixture.dispatcher.dispatchOne(41L, "worker-ai");

        assertEquals(
                AiReviewTaskDispatchOutcome.MANUAL_REQUIRED,
                outcome);
        assertTrue(providerSawTransaction.get());
    }

    private Fixture fixture(
            AiModerationProvider provider,
            int attemptCount,
            int maxAttempts) {
        AiReviewTaskMapper mapper = mock(AiReviewTaskMapper.class);
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager();
        AiReviewTaskTransactionService transactions =
                new AiReviewTaskTransactionService(
                        mapper,
                        transactionManager,
                        new ObjectMapper());
        when(mapper.finalizeExhausted(
                eq(41L),
                any(),
                eq(maxAttempts),
                eq(AiReviewTaskTransactionService.MAX_ATTEMPTS_ERROR_CODE)))
                .thenReturn(0);
        when(mapper.claim(
                eq(41L),
                eq("worker-ai"),
                any(),
                any(),
                eq(maxAttempts)))
                .thenReturn(1);
        when(mapper.selectById(41L))
                .thenReturn(runningTask(attemptCount));

        AiProviderRegistry registry = new AiProviderRegistry(
                "test",
                Collections.singletonList(provider));
        AiModerationService moderationService = new AiModerationService(
                registry,
                new AiContentSecurity(),
                new AiProviderMetrics(new SimpleMeterRegistry()));
        AiReviewTaskInputLoader inputLoader =
                mock(AiReviewTaskInputLoader.class);
        when(inputLoader.load(any(AiReviewTaskClaim.class)))
                .thenReturn(request());
        AiProviderTransactionBoundary providerBoundary =
                new AiProviderTransactionBoundary(
                        moderationService,
                        transactionManager);
        AiReviewTaskDispatcher dispatcher = new AiReviewTaskDispatcher(
                transactions,
                inputLoader,
                providerBoundary,
                maxAttempts,
                30,
                10,
                0.8d);
        return new Fixture(
                mapper,
                transactionManager,
                inputLoader,
                dispatcher);
    }

    private AiModerationProvider provider(
            AtomicBoolean transactionActive,
            AiModerationResult result) {
        return new AiModerationProvider() {
            @Override
            public String providerCode() {
                return "test";
            }

            @Override
            public AiProviderAvailability availability() {
                return AiProviderAvailability.AVAILABLE;
            }

            @Override
            public AiModerationResult review(
                    AiModerationRequest request) {
                transactionActive.set(
                        TransactionSynchronizationManager
                                .isActualTransactionActive());
                return result;
            }
        };
    }

    private AiModerationProvider failingProvider(
            AtomicBoolean transactionActive) {
        return new AiModerationProvider() {
            @Override
            public String providerCode() {
                return "test";
            }

            @Override
            public AiProviderAvailability availability() {
                return AiProviderAvailability.AVAILABLE;
            }

            @Override
            public AiModerationResult review(
                    AiModerationRequest request) {
                transactionActive.set(
                        TransactionSynchronizationManager
                                .isActualTransactionActive());
                throw AiProviderException.unavailable();
            }
        };
    }

    private AiModerationResult approvedResult() {
        return new AiModerationResult(
                AiModerationDecision.APPROVE,
                0.92d,
                "CONTENT_SAFE",
                Collections.singletonList("SAFE"),
                null,
                null,
                "provider-request-41",
                "model-v1",
                24L);
    }

    private AiModerationRequest request() {
        AiContentSecurity security = new AiContentSecurity();
        return new AiModerationRequest(
                AiModerationScene.ANNOUNCEMENT_REVIEW,
                "review-dispatch-041",
                "policy-v1",
                "safe title",
                "safe body",
                security.contentHash("safe title", "safe body"),
                "zh-CN");
    }

    private AiReviewTask runningTask(int attemptCount) {
        AiModerationRequest request = request();
        AiReviewTask task = new AiReviewTask();
        task.setReviewTaskId(41L);
        task.setReviewRequestId(request.getReviewRequestId());
        task.setScene(request.getScene().name());
        task.setBusinessType("ANNOUNCEMENT");
        task.setBusinessId(501L);
        task.setContentVersion(3);
        task.setContentHash(request.getContentHash());
        task.setPolicyVersionId(7L);
        task.setWorkerId("worker-ai");
        task.setAttemptCount(attemptCount);
        task.setTaskStatus("RUNNING");
        task.setVersion(8);
        return task;
    }

    private static final class Fixture {
        private final AiReviewTaskMapper taskMapper;
        private final RecordingTransactionManager transactionManager;
        private final AiReviewTaskInputLoader inputLoader;
        private final AiReviewTaskDispatcher dispatcher;

        private Fixture(
                AiReviewTaskMapper taskMapper,
                RecordingTransactionManager transactionManager,
                AiReviewTaskInputLoader inputLoader,
                AiReviewTaskDispatcher dispatcher) {
            this.taskMapper = taskMapper;
            this.transactionManager = transactionManager;
            this.inputLoader = inputLoader;
            this.dispatcher = dispatcher;
        }
    }
}
