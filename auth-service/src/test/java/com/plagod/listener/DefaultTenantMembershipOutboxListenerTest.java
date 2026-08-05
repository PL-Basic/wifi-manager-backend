package com.plagod.listener;

import com.plagod.configuration.TenantMembershipOutboxAsyncConfiguration;
import com.plagod.event.DefaultTenantMembershipOutboxCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTenantMembershipOutboxListenerTest {

    @Test
    void listenerUsesDedicatedAsyncExecutor() throws NoSuchMethodException {
        Method method = DefaultTenantMembershipOutboxListener.class.getMethod(
                "afterCommit",
                DefaultTenantMembershipOutboxCreatedEvent.class);

        Async async = method.getAnnotation(Async.class);

        assertNotNull(async);
        assertEquals("tenantMembershipOutboxExecutor", async.value());
    }

    @Test
    void outboxExecutorUsesBoundedQueue() {
        TenantMembershipOutboxAsyncConfiguration configuration =
                new TenantMembershipOutboxAsyncConfiguration();
        Executor executor = configuration.tenantMembershipOutboxExecutor(1, 2, 100);

        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(1, taskExecutor.getCorePoolSize());
        assertEquals(2, taskExecutor.getMaxPoolSize());
        assertEquals(100, taskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());

        taskExecutor.shutdown();
    }

    @Test
    void saturatedExecutorLeavesDeliveryToDatabaseRetryWithoutCallerRuns() throws InterruptedException {
        TenantMembershipOutboxAsyncConfiguration configuration =
                new TenantMembershipOutboxAsyncConfiguration();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                configuration.tenantMembershipOutboxExecutor(1, 1, 1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean(false);

        try {
            executor.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
            executor.execute(() -> { });

            assertDoesNotThrow(() -> executor.execute(() -> rejectedTaskRan.set(true)));
            assertFalse(rejectedTaskRan.get());
        } finally {
            releaseWorker.countDown();
            executor.shutdown();
        }
    }
}
