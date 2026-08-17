package com.plagod.test;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RecordingTransactionManager
        extends AbstractPlatformTransactionManager {

    private static final long serialVersionUID = 1L;

    private final AtomicLong transactionSequence = new AtomicLong();
    private final AtomicInteger commitCount = new AtomicInteger();
    private final AtomicInteger rollbackCount = new AtomicInteger();

    @Override
    protected Object doGetTransaction() {
        return new RecordingTransaction(
                (TransactionHolder) TransactionSynchronizationManager
                        .getResource(this));
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((RecordingTransaction) transaction).holder != null;
    }

    @Override
    protected void doBegin(
            Object transaction,
            TransactionDefinition definition) {
        RecordingTransaction recordingTransaction =
                (RecordingTransaction) transaction;
        TransactionHolder holder = new TransactionHolder(
                transactionSequence.incrementAndGet());
        recordingTransaction.holder = holder;
        TransactionSynchronizationManager.bindResource(this, holder);
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
        RecordingTransaction recordingTransaction =
                (RecordingTransaction) transaction;
        TransactionHolder holder =
                (TransactionHolder) TransactionSynchronizationManager
                        .unbindResource(this);
        recordingTransaction.holder = null;
        return holder;
    }

    @Override
    protected void doResume(
            Object transaction,
            Object suspendedResources) {
        TransactionHolder holder =
                (TransactionHolder) suspendedResources;
        if (transaction != null) {
            ((RecordingTransaction) transaction).holder = holder;
        }
        TransactionSynchronizationManager.bindResource(this, holder);
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        RecordingTransaction recordingTransaction =
                (RecordingTransaction) transaction;
        if (TransactionSynchronizationManager.hasResource(this)) {
            TransactionSynchronizationManager.unbindResource(this);
        }
        recordingTransaction.holder = null;
    }

    public Long currentTransactionId() {
        TransactionHolder holder =
                (TransactionHolder) TransactionSynchronizationManager
                        .getResource(this);
        return holder == null ? null : holder.id;
    }

    public int getCommitCount() {
        return commitCount.get();
    }

    public int getRollbackCount() {
        return rollbackCount.get();
    }

    private static final class RecordingTransaction {

        private TransactionHolder holder;

        private RecordingTransaction(TransactionHolder holder) {
            this.holder = holder;
        }
    }

    private static final class TransactionHolder {

        private final long id;

        private TransactionHolder(long id) {
            this.id = id;
        }
    }
}
