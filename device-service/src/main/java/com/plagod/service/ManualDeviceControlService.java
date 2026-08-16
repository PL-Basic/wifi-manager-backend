package com.plagod.service;

import com.plagod.dto.device.ManualBlockTrafficDTO;
import com.plagod.dto.device.ManualDisconnectMacDTO;
import com.plagod.vo.device.DeviceCommandResult;

public interface ManualDeviceControlService {

    DeviceCommandResult disconnectMac(Long tenantId, String deviceCode, ManualDisconnectMacDTO dto, Integer operatorRole);

    DeviceCommandResult blockTraffic(Long tenantId, String deviceCode, ManualBlockTrafficDTO dto, Integer operatorRole);
}
