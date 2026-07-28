package com.plagod.service;

import com.plagod.vo.device.DeviceCommandResult;

public interface ManagedDeviceCommandService {

    DeviceCommandResult enqueueDisconnectMac(String deviceCode, String mac, Long alertId, String purpose);

    DeviceCommandResult enqueueBlockTraffic(String deviceCode, String dstIp, String sni, Long alertId, String purpose);
}