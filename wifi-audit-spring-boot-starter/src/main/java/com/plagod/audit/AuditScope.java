package com.plagod.audit;

final class AuditScope {

    private final Long tenantId;
    private final String scopeType;
    private final boolean platformManaged;

    AuditScope(
            Long tenantId,
            String scopeType,
            boolean platformManaged) {
        this.tenantId = tenantId;
        this.scopeType = scopeType;
        this.platformManaged = platformManaged;
    }

    Long getTenantId() {
        return tenantId;
    }

    String getScopeType() {
        return scopeType;
    }

    boolean isPlatformManaged() {
        return platformManaged;
    }
}
