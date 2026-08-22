package com.plagod.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportReviewWorkerTest {

    @Test
    void scheduledWorkerScansAndContinuesAfterDispatchFailure() {
        SupportReviewOutboxTransactionService transactionService =
                mock(SupportReviewOutboxTransactionService.class);
        SupportReviewDispatcher dispatcher =
                mock(SupportReviewDispatcher.class);
        when(transactionService.findDispatchableEventIds(
                any(LocalDateTime.class),
                eq(3))).thenReturn(Arrays.asList(31L, 32L, 33L));
        when(dispatcher.dispatch(31L, "support-worker-a"))
                .thenThrow(new IllegalStateException("finalize unavailable"));
        SupportReviewWorker worker = new SupportReviewWorker(
                transactionService,
                dispatcher,
                fixedClock(),
                3,
                "support-worker-a");

        worker.dispatchDueReviews();

        verify(dispatcher).dispatch(31L, "support-worker-a");
        verify(dispatcher).dispatch(32L, "support-worker-a");
        verify(dispatcher).dispatch(33L, "support-worker-a");
        assertEquals("support-worker-a", worker.getWorkerId());
        ArgumentCaptor<LocalDateTime> scanTime =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionService).findDispatchableEventIds(
                scanTime.capture(),
                eq(3));
        assertEquals(
                LocalDateTime.of(2026, 8, 17, 17, 0),
                scanTime.getValue());
    }

    @Test
    void workerFailureLogDoesNotExposeSensitiveExceptionMessage() {
        SupportReviewOutboxTransactionService transactionService =
                mock(SupportReviewOutboxTransactionService.class);
        SupportReviewDispatcher dispatcher =
                mock(SupportReviewDispatcher.class);
        when(transactionService.findDispatchableEventIds(
                any(LocalDateTime.class),
                eq(1))).thenReturn(Arrays.asList(41L));
        when(dispatcher.dispatch(41L, "support-worker-log"))
                .thenThrow(new IllegalStateException(
                        "Authorization: Bearer SUPPORT_SECRET_CANARY "
                                + "verificationCode=654321 "
                                + "accessToken=SUPPORT_TOKEN_CANARY"));

        Logger logger = (Logger) LoggerFactory.getLogger(
                SupportReviewWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new SupportReviewWorker(
                    transactionService,
                    dispatcher,
                    fixedClock(),
                    1,
                    "support-worker-log")
                    .dispatchDueReviews();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(1, appender.list.size());
        String rendered = appender.list.get(0).getFormattedMessage();
        assertFalse(rendered.contains("SUPPORT_SECRET_CANARY"));
        assertFalse(rendered.contains("654321"));
        assertFalse(rendered.contains("SUPPORT_TOKEN_CANARY"));
        assertFalse(rendered.contains("Authorization"));
        assertFalse(rendered.contains("verificationCode"));
        assertFalse(rendered.contains("accessToken"));
        assertTrue(rendered.contains(
                IllegalStateException.class.getName()));
    }

    @Test
    void productionSchedulingContractIsEnabled() throws Exception {
        Method workerMethod = SupportReviewWorker.class.getDeclaredMethod(
                "dispatchDueReviews");

        assertNotNull(workerMethod.getAnnotation(Scheduled.class));
        assertNotNull(SupportReviewSchedulingConfiguration.class
                .getAnnotation(EnableScheduling.class));
    }

    private Clock fixedClock() {
        return Clock.fixed(
                Instant.parse("2026-08-17T09:00:00Z"),
                StableUnits.ASIA_SHANGHAI);
    }
}
