package com.plagod.audit;

import org.springframework.jdbc.core.JdbcTemplate;

final class JdbcAuditWriter implements AuditWriter {

    private static final String INSERT_SQL =
            "insert into t_audit_log "
                    + "(tenant_id, scope_type, operator_id, operator_name, "
                    + "action, target, detail, ip) "
                    + "values (?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    JdbcAuditWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(AuditWriteRecord record) {
        jdbcTemplate.update(
                INSERT_SQL,
                record.getTenantId(),
                record.getScopeType(),
                record.getOperatorId(),
                record.getOperatorName(),
                record.getAction(),
                record.getTarget(),
                record.getDetail(),
                record.getIp());
    }
}
