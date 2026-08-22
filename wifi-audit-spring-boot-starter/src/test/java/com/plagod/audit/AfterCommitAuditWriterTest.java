package com.plagod.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AfterCommitAuditWriterTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private DataSourceTransactionManager transactionManager;
    private TransactionTemplate businessTransaction;
    private AuditWriteFailureReporter reporter;

    @BeforeEach
    void setUpDatabase() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        transactionManager =
                new DataSourceTransactionManager(database);
        businessTransaction =
                new TransactionTemplate(transactionManager);
        reporter = new AuditWriteFailureReporter(null);

        jdbcTemplate.execute(
                "create table business_event ("
                        + "id bigint primary key, "
                        + "payload varchar(64) not null)");
        jdbcTemplate.execute(
                "create table t_audit_log ("
                        + "id bigint auto_increment primary key, "
                        + "tenant_id bigint, "
                        + "scope_type varchar(32) not null, "
                        + "operator_id bigint, "
                        + "operator_name varchar(64), "
                        + "action varchar(64) not null, "
                        + "target varchar(255), "
                        + "detail clob, "
                        + "ip varchar(45))");
    }

    @AfterEach
    void shutDownDatabase() {
        database.shutdown();
    }

    @Test
    void committedBusinessCreatesSuccessAuditRow() {
        AfterCommitAuditWriter writer = writer(
                new JdbcAuditWriter(jdbcTemplate));

        businessTransaction.execute(status -> {
            jdbcTemplate.update(
                    "insert into business_event "
                            + "(id, payload) values (?, ?)",
                    1L,
                    "committed");
            writer.write(record());
            assertEquals(0, countAuditRows());
            return null;
        });

        assertEquals(1, countBusinessRows());
        assertEquals(1, countAuditRows());
    }

    @Test
    void rolledBackBusinessDoesNotCreateSuccessAuditRow() {
        AfterCommitAuditWriter writer = writer(
                new JdbcAuditWriter(jdbcTemplate));

        assertThrows(
                IllegalStateException.class,
                () -> businessTransaction.execute(status -> {
                    jdbcTemplate.update(
                            "insert into business_event "
                                    + "(id, payload) values (?, ?)",
                            2L,
                            "rolled-back");
                    writer.write(record());
                    throw new IllegalStateException(
                            "force business rollback");
                }));

        assertEquals(0, countBusinessRows());
        assertEquals(0, countAuditRows());
    }

    @Test
    void auditFailureDoesNotAffectCommittedBusiness() {
        AuditWriter failingWriter = ignored -> {
            throw new IllegalStateException(
                    "database secret must not escape");
        };
        AfterCommitAuditWriter writer = writer(failingWriter);

        businessTransaction.execute(status -> {
            jdbcTemplate.update(
                    "insert into business_event "
                            + "(id, payload) values (?, ?)",
                    3L,
                    "committed");
            writer.write(record());
            return null;
        });

        assertEquals(1, countBusinessRows());
        assertEquals(0, countAuditRows());
        assertEquals(1, reporter.getWriteFailures());
    }

    private AfterCommitAuditWriter writer(
            AuditWriter delegate) {
        return new AfterCommitAuditWriter(
                delegate,
                transactionManager,
                reporter);
    }

    private int countBusinessRows() {
        return jdbcTemplate.queryForObject(
                "select count(*) from business_event",
                Integer.class);
    }

    private int countAuditRows() {
        return jdbcTemplate.queryForObject(
                "select count(*) from t_audit_log",
                Integer.class);
    }

    private AuditWriteRecord record() {
        return new AuditWriteRecord(
                7L,
                "TENANT",
                42L,
                "user#42",
                "device.update",
                "DEVICE:7",
                "{\"outcome\":\"SUCCESS\"}",
                "127.0.0.1");
    }
}
