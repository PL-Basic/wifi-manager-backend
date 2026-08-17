package com.plagod.audit;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DENIED/FAILED 审计使用独立事务，不参与业务事务回滚。
 */
final class IndependentAuditWriter {

    static final String MODE = "independent_failure";

    private final AuditWriter delegate;
    private final AuditWriteFailureReporter failureReporter;
    private final TransactionTemplate transactionTemplate;

    IndependentAuditWriter(
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
