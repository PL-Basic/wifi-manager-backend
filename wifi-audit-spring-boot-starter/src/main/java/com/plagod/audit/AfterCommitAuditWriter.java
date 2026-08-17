package com.plagod.audit;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 成功审计只在业务事务提交后追加。
 */
final class AfterCommitAuditWriter {

    static final String MODE = "after_commit";

    private final AuditWriter delegate;
    private final AuditWriteFailureReporter failureReporter;
    private final TransactionTemplate transactionTemplate;

    AfterCommitAuditWriter(
            AuditWriter delegate,
            PlatformTransactionManager transactionManager,
            AuditWriteFailureReporter failureReporter) {
        this.delegate = delegate;
        this.failureReporter = failureReporter;
        this.transactionTemplate = new TransactionTemplate(
                transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void write(AuditWriteRecord record) {
        if (TransactionSynchronizationManager
                .isActualTransactionActive()
                && TransactionSynchronizationManager
                .isSynchronizationActive()) {
            try {
                TransactionSynchronizationManager
                        .registerSynchronization(
                                new TransactionSynchronization() {
                                    @Override
                                    public void afterCommit() {
                                        appendInNewTransactionSafely(record);
                                    }
                                });
            } catch (RuntimeException exception) {
                failureReporter.writerFailure(MODE);
            }
            return;
        }
        appendInNewTransactionSafely(record);
    }

    private void appendInNewTransactionSafely(
            AuditWriteRecord record) {
        final boolean[] appendFailed = {false};
        try {
            transactionTemplate.execute(status -> {
                try {
                    delegate.append(record);
                } catch (RuntimeException exception) {
                    appendFailed[0] = true;
                    status.setRollbackOnly();
                }
                return null;
            });
        } catch (RuntimeException exception) {
            failureReporter.writerFailure(MODE);
            return;
        }
        if (appendFailed[0]) {
            failureReporter.writerFailure(MODE);
        }
    }
}
