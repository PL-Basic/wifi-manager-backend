package com.plagod.audit;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcAuditWriterTest {

    @Test
    void appendsTheFrozenAuditColumnsWithTenantScope() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcAuditWriter writer = new JdbcAuditWriter(jdbcTemplate);
        AuditWriteRecord record = new AuditWriteRecord(
                7L,
                "TENANT",
                42L,
                "operator",
                "device.update",
                "device-7",
                "{\"result\":\"ok\"}",
                "127.0.0.1");

        writer.append(record);

        verify(jdbcTemplate).update(
                eq("insert into t_audit_log "
                        + "(tenant_id, scope_type, operator_id, operator_name, "
                        + "action, target, detail, ip) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)"),
                eq(7L),
                eq("TENANT"),
                eq(42L),
                eq("operator"),
                eq("device.update"),
                eq("device-7"),
                eq("{\"result\":\"ok\"}"),
                eq("127.0.0.1"));
    }
}
