package com.plagod.audit;

final class AuditWriteRecord {

    private final Long tenantId;
    private final String scopeType;
    private final Long operatorId;
    private final String operatorName;
    private final String action;
    private final String target;
    private final String detail;
    private final String ip;

    AuditWriteRecord(Long tenantId,
                     String scopeType,
                     Long operatorId,
                     String operatorName,
                     String action,
                     String target,
                     String detail,
                     String ip) {
        this.tenantId = tenantId;
        this.scopeType = scopeType;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.action = action;
        this.target = target;
        this.detail = detail;
        this.ip = ip;
    }

    Long getTenantId() {
        return tenantId;
    }

    String getScopeType() {
        return scopeType;
    }

    Long getOperatorId() {
        return operatorId;
    }

    String getOperatorName() {
        return operatorName;
    }

    String getAction() {
        return action;
    }

    String getTarget() {
        return target;
    }

    String getDetail() {
        return detail;
    }

    String getIp() {
        return ip;
    }
}
