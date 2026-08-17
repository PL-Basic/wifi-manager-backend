package com.plagod.service;

import com.plagod.entity.MarketplaceFulfillment;
import com.plagod.mapper.MarketplaceFulfillmentMapper;
import com.plagod.test.RecordingTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceFulfillmentDispatcherTest {

    @Test
    void networkFulfillmentRunsAfterClaimCommitAndBeforeFinalizeTransaction() {
        MarketplaceFulfillmentMapper mapper = claimedMapper(1);
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager();
        AtomicBoolean transactionActiveDuringNetwork =
                new AtomicBoolean(true);
        MarketplaceFulfillmentPort port = request -> {
            transactionActiveDuringNetwork.set(
                    TransactionSynchronizationManager
                            .isActualTransactionActive());
            return new MarketplaceFulfillmentResult("ENT-RESULT-301");
        };
        MarketplaceFulfillmentDispatcher dispatcher =
                dispatcher(mapper, port, transactionManager, 5);

        MarketplaceFulfillmentDispatchOutcome outcome =
                dispatcher.dispatchOne(301L, "worker-network");

        assertEquals(
                MarketplaceFulfillmentDispatchOutcome.SUCCEEDED,
                outcome);
        assertFalse(transactionActiveDuringNetwork.get());
        assertFalse(TransactionSynchronizationManager
                .isActualTransactionActive());
        verify(mapper).completeSuccess(
                eq(301L),
                eq("worker-network"),
                eq("ENT-RESULT-301"),
                any(LocalDateTime.class));
    }

    @Test
    void fifthNetworkFailureFinalizesTerminalWithoutSuccessWrite() throws Exception {
        MarketplaceFulfillmentMapper mapper = claimedMapper(5);
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager();
        when(mapper.completeFailure(
                eq(301L),
                eq("worker-network"),
                eq("DEPENDENCY_UNAVAILABLE"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(5)))
                .thenReturn(1);
        MarketplaceFulfillmentPort port =
                mock(MarketplaceFulfillmentPort.class);
        when(port.fulfill(any()))
                .thenThrow(new IllegalStateException("remote unavailable"));
        MarketplaceFulfillmentDispatcher dispatcher =
                dispatcher(mapper, port, transactionManager, 5);

        MarketplaceFulfillmentDispatchOutcome outcome =
                dispatcher.dispatchOne(301L, "worker-network");

        assertEquals(
                MarketplaceFulfillmentDispatchOutcome.FAILED_TERMINAL,
                outcome);
        verify(mapper, never()).completeSuccess(
                any(), any(), any(), any());
    }

    @Test
    void callerTransactionIsSuspendedAcrossRemoteFulfillment() {
        MarketplaceFulfillmentMapper mapper = claimedMapper(1);
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager();
        AtomicBoolean transactionActiveDuringNetwork =
                new AtomicBoolean(true);
        MarketplaceFulfillmentPort port = request -> {
            transactionActiveDuringNetwork.set(
                    TransactionSynchronizationManager
                            .isActualTransactionActive());
            return new MarketplaceFulfillmentResult("ENT-RESULT-301");
        };
        MarketplaceFulfillmentDispatcher dispatcher =
                dispatcher(mapper, port, transactionManager, 5);
        TransactionTemplate caller =
                new TransactionTemplate(transactionManager);

        caller.execute(status -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            dispatcher.dispatchOne(301L, "worker-network");
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return null;
        });

        assertFalse(transactionActiveDuringNetwork.get());
        assertFalse(TransactionSynchronizationManager
                .isActualTransactionActive());
    }

    @Test
    void oversizedSuccessfulResultIsFinalizedAsProtocolFailure() {
        MarketplaceFulfillmentMapper mapper = claimedMapper(1);
        RecordingTransactionManager transactionManager =
                new RecordingTransactionManager();
        when(mapper.completeFailure(
                eq(301L),
                eq("worker-network"),
                eq("DEPENDENCY_PROTOCOL_INVALID"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(5)))
                .thenReturn(1);
        MarketplaceFulfillmentPort port = request ->
                new MarketplaceFulfillmentResult(repeat('x', 129));
        MarketplaceFulfillmentDispatcher dispatcher =
                dispatcher(mapper, port, transactionManager, 5);

        MarketplaceFulfillmentDispatchOutcome outcome =
                dispatcher.dispatchOne(301L, "worker-network");

        assertEquals(
                MarketplaceFulfillmentDispatchOutcome.RETRY_SCHEDULED,
                outcome);
        verify(mapper, never()).completeSuccess(
                any(), any(), any(), any());
        verify(mapper).completeFailure(
                eq(301L),
                eq("worker-network"),
                eq("DEPENDENCY_PROTOCOL_INVALID"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(5));
    }

    private MarketplaceFulfillmentDispatcher dispatcher(
            MarketplaceFulfillmentMapper mapper,
            MarketplaceFulfillmentPort port,
            RecordingTransactionManager transactionManager,
            int maxAttempts) {
        MarketplaceFulfillmentTransactionService transactionService =
                new MarketplaceFulfillmentTransactionService(
                        mapper,
                        transactionManager);
        return new MarketplaceFulfillmentDispatcher(
                transactionService,
                port,
                transactionManager,
                maxAttempts,
                30,
                10);
    }

    private MarketplaceFulfillmentMapper claimedMapper(int attemptCount) {
        MarketplaceFulfillmentMapper mapper =
                mock(MarketplaceFulfillmentMapper.class);
        MarketplaceFulfillment fulfillment =
                new MarketplaceFulfillment();
        fulfillment.setFulfillmentId(301L);
        fulfillment.setTenantId(10L);
        fulfillment.setOrderId(101L);
        fulfillment.setOrderItemId(201L);
        fulfillment.setEventKey("event-market-301");
        fulfillment.setFulfillmentType("PERSONAL_ENTITLEMENT");
        fulfillment.setFulfillmentMode("TARGET_DOMAIN");
        fulfillment.setTargetBusinessKey("ENTITLEMENT:201");
        fulfillment.setStatus("PROCESSING");
        fulfillment.setWorkerId("worker-network");
        fulfillment.setAttemptCount(attemptCount);

        when(mapper.failExhausted(
                eq(301L),
                any(LocalDateTime.class),
                eq(5),
                eq(MarketplaceFulfillmentTransactionService
                        .MAX_ATTEMPTS_ERROR_KEY)))
                .thenReturn(0);
        when(mapper.claim(
                eq(301L),
                eq("worker-network"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(5)))
                .thenReturn(1);
        when(mapper.selectById(301L)).thenReturn(fulfillment);
        when(mapper.completeSuccess(
                eq(301L),
                eq("worker-network"),
                eq("ENT-RESULT-301"),
                any(LocalDateTime.class)))
                .thenReturn(1);
        return mapper;
    }

    private String repeat(char value, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
