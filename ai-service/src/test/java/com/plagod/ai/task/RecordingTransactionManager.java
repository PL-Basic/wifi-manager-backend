package com.plagod.ai.task;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

final class RecordingTransactionManager
        extends AbstractPlatformTransactionManager {

    private final ThreadLocal<Boolean> active =
            ThreadLocal.withInitial(() -> false);
    private int begins;
    private int commits;
    private int rollbacks;

    @Override
    protected Object doGetTransaction() {
        return new TransactionState(active.get());
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((TransactionState) transaction).active;
    }

    @Override
    protected void doBegin(
            Object transaction,
            TransactionDefinition definition) {
        begins++;
        ((TransactionState) transaction).active = true;
        active.set(true);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        commits++;
        active.set(false);
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbacks++;
        active.set(false);
    }

    @Override
    protected Object doSuspend(Object transaction) {
        boolean suspended = active.get();
        active.set(false);
        return suspended;
    }

    @Override
    protected void doResume(
            Object transaction,
            Object suspendedResources) {
        active.set((Boolean) suspendedResources);
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        ((TransactionState) transaction).active = false;
    }

    int getBegins() {
        return begins;
    }

    int getCommits() {
        return commits;
    }

    int getRollbacks() {
        return rollbacks;
    }

    private static final class TransactionState {
        private boolean active;

        private TransactionState(boolean active) {
            this.active = active;
        }
    }
}
