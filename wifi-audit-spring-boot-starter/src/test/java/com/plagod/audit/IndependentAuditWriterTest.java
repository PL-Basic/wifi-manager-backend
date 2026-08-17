package com.plagod.audit;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentAuditWriterTest {

    @Test
    void usesRequiresNewTransaction() {
        AuditWriter delegate = mock(AuditWriter.class);
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        AuditWriteFailureReporter reporter =
                mock(AuditWriteFailureReporter.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any()))
                .thenReturn(status);
        IndependentAuditWriter writer =
                new IndependentAuditWriter(
                        delegate,
                        transactionManager,
                        reporter);

        writer.write(record());

        org.mockito.ArgumentCaptor<TransactionDefinition> definition =
                org.mockito.ArgumentCaptor.forClass(
                        TransactionDefinition.class);
        verify(transactionManager).getTransaction(
                definition.capture());
        assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definition.getValue().getPropagationBehavior());
        verify(delegate).append(any(AuditWriteRecord.class));
        verify(transactionManager).commit(status);
    }

    @Test
    void transactionFailureNeverEscapes() {
        AuditWriter delegate = mock(AuditWriter.class);
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        AuditWriteFailureReporter reporter =
                mock(AuditWriteFailureReporter.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any()))
                .thenReturn(status);
        doThrow(new IllegalStateException("database secret"))
                .when(delegate).append(any(AuditWriteRecord.class));
        IndependentAuditWriter writer =
                new IndependentAuditWriter(
                        delegate,
                        transactionManager,
                        reporter);

        writer.write(record());

        assertTrue(status.isRollbackOnly());
        verify(transactionManager).commit(status);
        verify(reporter).writerFailure(
                IndependentAuditWriter.MODE);
    }

    private AuditWriteRecord record() {
        return new AuditWriteRecord(
                7L,
                "TENANT",
                42L,
                "user#42",
                "device.update",
                "DEVICE:7",
                "{\"outcome\":\"DENIED\"}",
                "127.0.0.1");
    }
}
