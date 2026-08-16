package com.plagod.service;

import com.plagod.audit.Audited;
import com.plagod.audit.AuditTenantId;
import com.plagod.constant.DeviceCommandPurpose;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RuleActionExecutor {

    @Autowired
    private ManagedDeviceCommandService managedDeviceCommandService;

    @Audited(
            action = "monitor.auto.disconnect-mac",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.ARGUMENT,
            operatorName = "monitor-auto")
    public void disconnectMac(@AuditTenantId Long tenantId,
                              String deviceCode,
                              String mac,
                              Long alertId) {
        managedDeviceCommandService.enqueueDisconnectMac(tenantId, deviceCode, mac, alertId, DeviceCommandPurpose.MONITOR_AUTO_DISCONNECT);
    }

    @Audited(
            action = "monitor.auto.block-traffic",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.ARGUMENT,
            operatorName = "monitor-auto")
    public void blockTraffic(@AuditTenantId Long tenantId,
                             String deviceCode,
                             String dstIp,
                             String sni,
                             Long alertId) {
        managedDeviceCommandService.enqueueBlockTraffic(tenantId, deviceCode, dstIp, sni, alertId, DeviceCommandPurpose.MONITOR_AUTO_BLOCK_TRAFFIC);
    }
}
