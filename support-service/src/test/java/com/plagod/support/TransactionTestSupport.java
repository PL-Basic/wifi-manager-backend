package com.plagod.support;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

final class TransactionTestSupport {

    private TransactionTestSupport() {
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(T target, RecordingTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(
                manager,
                new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    static final class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {

        private final ThreadLocal<Object> activeTransaction =
                new ThreadLocal<>();
        private int beginCount;
        private int commitCount;
        private int rollbackCount;

        int getBeginCount() {
            return beginCount;
        }

        int getCommitCount() {
            return commitCount;
        }

        int getRollbackCount() {
            return rollbackCount;
        }

        @Override
        protected Object doGetTransaction() {
            return new TestTransaction(activeTransaction.get() != null);
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TestTransaction) transaction).existing;
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
            activeTransaction.set(transaction);
            beginCount++;
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
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            if (activeTransaction.get() == transaction) {
                activeTransaction.remove();
            }
        }

        private static final class TestTransaction {

            private final boolean existing;

            private TestTransaction(boolean existing) {
                this.existing = existing;
            }
        }
    }
}
