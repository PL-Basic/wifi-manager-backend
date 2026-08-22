package com.plagod.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceFulfillmentWorkerTest {

    @Test
    void scheduledWorkerScansAndContinuesAfterOneDispatchFailure() {
        MarketplaceFulfillmentTransactionService transactionService =
                mock(MarketplaceFulfillmentTransactionService.class);
        MarketplaceFulfillmentDispatcher dispatcher =
                mock(MarketplaceFulfillmentDispatcher.class);
        when(transactionService.findDispatchableIds(
                any(LocalDateTime.class),
                eq(3)))
                .thenReturn(Arrays.asList(301L, 302L, 303L));
        when(dispatcher.dispatchOne(301L, "market-worker-a"))
                .thenThrow(new IllegalStateException(
                        "finalize unavailable"));
        MarketplaceFulfillmentWorker worker =
                new MarketplaceFulfillmentWorker(
                        transactionService,
                        dispatcher,
                        3,
                        "market-worker-a");

        worker.dispatchDueFulfillments();

        verify(dispatcher).dispatchOne(301L, "market-worker-a");
        verify(dispatcher).dispatchOne(302L, "market-worker-a");
        verify(dispatcher).dispatchOne(303L, "market-worker-a");
        assertEquals("market-worker-a", worker.getWorkerId());
    }

    @Test
    void productionSchedulingContractIsEnabled() throws Exception {
        Method workerMethod =
                MarketplaceFulfillmentWorker.class.getDeclaredMethod(
                        "dispatchDueFulfillments");

        assertNotNull(workerMethod.getAnnotation(Scheduled.class));
        assertNotNull(
                MarketplaceFulfillmentSchedulingConfiguration.class
                        .getAnnotation(EnableScheduling.class));
    }
}
