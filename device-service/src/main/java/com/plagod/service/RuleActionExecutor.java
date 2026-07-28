package com.plagod.service;

import com.plagod.audit.Audited;
import com.plagod.constant.DeviceCommandPurpose;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RuleActionExecutor {

    @Autowired
    private ManagedDeviceCommandService managedDeviceCommandService;

    @Audited(action = "monitor.auto.disconnect-mac", operatorName = "monitor-auto")
    public void disconnectMac(String deviceCode, String mac, Long alertId) {
        managedDeviceCommandService.enqueueDisconnectMac(deviceCode, mac, alertId, DeviceCommandPurpose.MONITOR_AUTO_DISCONNECT);
    }

    @Audited(action = "monitor.auto.block-traffic", operatorName = "monitor-auto")
    public void blockTraffic(String deviceCode, String dstIp, String sni, Long alertId) {
        managedDeviceCommandService.enqueueBlockTraffic(deviceCode, dstIp, sni, alertId, DeviceCommandPurpose.MONITOR_AUTO_BLOCK_TRAFFIC);
    }
}