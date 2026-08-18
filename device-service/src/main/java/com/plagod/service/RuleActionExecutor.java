package com.plagod.service;

import com.plagod.audit.Audited;
import com.plagod.audit.AuditActorContext;
import com.plagod.audit.AuditDetail;
import com.plagod.audit.AuditTenantId;
import com.plagod.audit.AuditTargetId;
import com.plagod.constant.DeviceCommandPurpose;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RuleActionExecutor {

    @Autowired
    private ManagedDeviceCommandService managedDeviceCommandService;

    @Audited(
            action = "monitor.auto.disconnect-mac",
            targetType = "DEVICE",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.ARGUMENT,
            recordDenied = true,
            recordFailed = true)
    public void disconnectMac(@AuditTenantId Long tenantId,
                              @AuditTargetId String deviceCode,
                              String mac,
                              @AuditDetail("alertId") Long alertId,
                              AuditActorContext actorContext) {
        managedDeviceCommandService.enqueueDisconnectMac(tenantId, deviceCode, mac, alertId, DeviceCommandPurpose.MONITOR_AUTO_DISCONNECT);
    }

    @Audited(
            action = "monitor.auto.block-traffic",
            targetType = "DEVICE",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.ARGUMENT,
            recordDenied = true,
            recordFailed = true)
    public void blockTraffic(@AuditTenantId Long tenantId,
                             @AuditTargetId String deviceCode,
                             String dstIp,
                             String sni,
                             @AuditDetail("alertId") Long alertId,
                             AuditActorContext actorContext) {
        managedDeviceCommandService.enqueueBlockTraffic(tenantId, deviceCode, dstIp, sni, alertId, DeviceCommandPurpose.MONITOR_AUTO_BLOCK_TRAFFIC);
    }
}
