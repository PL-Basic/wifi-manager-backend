package com.plagod.audit;

final class AuditScope {

    private final Long tenantId;
    private final String scopeType;

    AuditScope(Long tenantId, String scopeType) {
        this.tenantId = tenantId;
        this.scopeType = scopeType;
    }

    Long getTenantId() {
        return tenantId;
    }

    String getScopeType() {
        return scopeType;
    }
}
