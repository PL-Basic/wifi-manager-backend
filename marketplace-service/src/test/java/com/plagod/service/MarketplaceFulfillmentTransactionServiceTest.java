package com.plagod.service;

import com.plagod.entity.MarketplaceFulfillment;
import com.plagod.mapper.MarketplaceFulfillmentMapper;
import com.plagod.test.RecordingTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceFulfillmentTransactionServiceTest {

    private MarketplaceFulfillmentMapper fulfillmentMapper;
    private MarketplaceFulfillmentTransactionService service;
    private RecordingTransactionManager transactionManager;
    private final AtomicReference<Long> callerTransactionId =
            new AtomicReference<>();

    @BeforeEach
    void setUp() {
        fulfillmentMapper = mock(MarketplaceFulfillmentMapper.class);
        transactionManager = new RecordingTransactionManager();
        service = new MarketplaceFulfillmentTransactionService(
                fulfillmentMapper,
                transactionManager);
    }

    @Test
    void conditionallyClaimsDueOrExpiredLeaseForCurrentWorker() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        LocalDateTime leaseUntil = now.plusSeconds(30);
        MarketplaceFulfillment processing = processing("worker-b", 2);

        when(fulfillmentMapper.failExhausted(
                301L,
                now,
                5,
                MarketplaceFulfillmentTransactionService
                        .MAX_ATTEMPTS_ERROR_KEY))
                .thenReturn(0);
        when(fulfillmentMapper.claim(
                301L, "worker-b", now, leaseUntil, 5))
                .thenReturn(1);
        when(fulfillmentMapper.selectById(301L)).thenReturn(processing);

        MarketplaceFulfillmentClaim claim = service.claim(
                301L, "worker-b", now, leaseUntil, 5);

        assertEquals(301L, claim.getFulfillmentId());
        assertEquals("worker-b", claim.getWorkerId());
        assertEquals(2, claim.getAttemptCount());
    }

    @Test
    void competingWorkerThatLosesConditionalUpdateGetsNoClaim() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 1);
        when(fulfillmentMapper.failExhausted(
                eq(301L), eq(now), eq(5), any(String.class)))
                .thenReturn(0);
        when(fulfillmentMapper.claim(
                eq(301L), eq("worker-loser"), eq(now), any(), eq(5)))
                .thenReturn(0);

        MarketplaceFulfillmentClaim claim = service.claim(
                301L,
                "worker-loser",
                now,
                now.plusSeconds(30),
                5);

        assertNull(claim);
        verify(fulfillmentMapper, never()).selectById(any());
    }

    @Test
    void wrongWorkerCannotFinalizeClaim() {
        MarketplaceFulfillmentClaim claim =
                MarketplaceFulfillmentClaim.from(
                        processing("worker-owner", 1),
                        "worker-stale");
        when(fulfillmentMapper.completeSuccess(
                eq(301L),
                eq("worker-stale"),
                eq("ENT-301"),
                any(LocalDateTime.class)))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.completeSuccess(
                        claim,
                        "ENT-301",
                        LocalDateTime.of(2026, 8, 17, 10, 2)));
    }

    @Test
    void failureOnMaximumAttemptBecomesTerminal() {
        MarketplaceFulfillmentClaim claim =
                MarketplaceFulfillmentClaim.from(
                        processing("worker-max", 5),
                        "worker-max");
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 3);
        when(fulfillmentMapper.completeFailure(
                301L,
                "worker-max",
                "DEPENDENCY_UNAVAILABLE",
                now,
                now.plusSeconds(10),
                5))
                .thenReturn(1);

        MarketplaceFulfillmentDispatchOutcome outcome =
                service.completeFailure(
                        claim,
                        "DEPENDENCY_UNAVAILABLE",
                        now,
                        now.plusSeconds(10),
                        5);

        assertEquals(
                MarketplaceFulfillmentDispatchOutcome.FAILED_TERMINAL,
                outcome);
    }

    @Test
    void expiredClaimAtMaximumAttemptIsTerminalizedWithoutNetworkClaim() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 4);
        when(fulfillmentMapper.failExhausted(
                301L,
                now,
                5,
                MarketplaceFulfillmentTransactionService
                        .MAX_ATTEMPTS_ERROR_KEY))
                .thenReturn(1);

        MarketplaceFulfillmentClaim claim = service.claim(
                301L,
                "worker-next",
                now,
                now.plusSeconds(30),
                5);

        assertNull(claim);
        verify(fulfillmentMapper, never()).claim(
                any(), any(), any(), any(), any());
    }

    @Test
    void claimUsesRequiresNewAndRestoresCallerTransaction() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 5);
        LocalDateTime leaseUntil = now.plusSeconds(30);
        MarketplaceFulfillment processing = processing("worker-new", 1);
        when(fulfillmentMapper.failExhausted(
                301L,
                now,
                5,
                MarketplaceFulfillmentTransactionService
                        .MAX_ATTEMPTS_ERROR_KEY))
                .thenReturn(0);
        when(fulfillmentMapper.claim(
                301L,
                "worker-new",
                now,
                leaseUntil,
                5))
                .thenAnswer(invocation -> {
                    assertTrue(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    assertNotEquals(
                            callerTransactionId.get(),
                            transactionManager.currentTransactionId());
                    return 1;
                });
        when(fulfillmentMapper.selectById(301L)).thenReturn(processing);

        TransactionTemplate caller =
                new TransactionTemplate(transactionManager);
        caller.execute(status -> {
            callerTransactionId.set(
                    transactionManager.currentTransactionId());
            service.claim(
                    301L,
                    "worker-new",
                    now,
                    leaseUntil,
                    5);
            assertEquals(
                    callerTransactionId.get(),
                    transactionManager.currentTransactionId());
            return null;
        });
    }

    private MarketplaceFulfillment processing(
            String workerId,
            int attemptCount) {
        MarketplaceFulfillment fulfillment = new MarketplaceFulfillment();
        fulfillment.setFulfillmentId(301L);
        fulfillment.setTenantId(10L);
        fulfillment.setOrderId(101L);
        fulfillment.setOrderItemId(201L);
        fulfillment.setEventKey("event-market-301");
        fulfillment.setFulfillmentType("PERSONAL_ENTITLEMENT");
        fulfillment.setFulfillmentMode("TARGET_DOMAIN");
        fulfillment.setTargetBusinessKey("ENTITLEMENT:201");
        fulfillment.setStatus("PROCESSING");
        fulfillment.setWorkerId(workerId);
        fulfillment.setAttemptCount(attemptCount);
        return fulfillment;
    }
}
