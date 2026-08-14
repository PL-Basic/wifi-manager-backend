package com.plagod.service;

import com.plagod.vo.device.DeviceCommandResult;

public interface ManagedDeviceCommandService {

    DeviceCommandResult enqueueDisconnectMac(Long tenantId, String deviceCode, String mac, Long alertId, String purpose);

    DeviceCommandResult enqueueBlockTraffic(Long tenantId, String deviceCode, String dstIp, String sni, Long alertId, String purpose);

    DeviceCommandResult enqueueKick(Long tenantId, String deviceCode, String reason, String purpose);
}
