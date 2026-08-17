package com.plagod.transaction;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.concurrent.atomic.AtomicInteger;

public final class TestTransactionManager
        extends AbstractPlatformTransactionManager {

    private final ThreadLocal<Object> activeTransaction =
            new ThreadLocal<>();
    private final AtomicInteger commitCount = new AtomicInteger();
    private final AtomicInteger rollbackCount = new AtomicInteger();

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return activeTransaction.get() != null;
    }

    @Override
    protected void doBegin(
            Object transaction,
            TransactionDefinition definition) {
        activeTransaction.set(new Object());
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        commitCount.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbackCount.incrementAndGet();
    }

    @Override
    protected Object doSuspend(Object transaction) {
        Object suspended = activeTransaction.get();
        activeTransaction.remove();
        return suspended;
    }

    @Override
    protected void doResume(
            Object transaction,
            Object suspendedResources) {
        activeTransaction.set(suspendedResources);
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        activeTransaction.remove();
    }

    public int getCommitCount() {
        return commitCount.get();
    }

    public int getRollbackCount() {
        return rollbackCount.get();
    }
}
