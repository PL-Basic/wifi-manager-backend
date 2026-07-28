package com.plagod.service.impl;

import com.plagod.audit.Audited;
import com.plagod.constant.DeviceCommandPurpose;
import com.plagod.dto.device.ManualBlockTrafficDTO;
import com.plagod.dto.device.ManualDisconnectMacDTO;
import com.plagod.service.ManagedDeviceCommandService;
import com.plagod.service.ManualDeviceControlService;
import com.plagod.vo.device.DeviceCommandResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ManualDeviceControlServiceImpl implements ManualDeviceControlService {

    private static final int SUPER_ADMIN_ROLE = 0;
    private static final int ADMIN_ROLE = 1;

    @Autowired
    private ManagedDeviceCommandService managedDeviceCommandService;

    @Override
    @Audited(action = "device.manual-disconnect-mac")
    public DeviceCommandResult disconnectMac(String deviceCode, ManualDisconnectMacDTO dto, Integer operatorRole) {

        validateAdminRole(operatorRole);

        if (dto == null) {
            throw new IllegalArgumentException("断线命令参数不能为空");
        }

        return managedDeviceCommandService.enqueueDisconnectMac(deviceCode, dto.getMac(), null, DeviceCommandPurpose.MANUAL_DISCONNECT);
    }

    @Override
    @Audited(action = "device.manual-block-traffic")
    public DeviceCommandResult blockTraffic(String deviceCode, ManualBlockTrafficDTO dto, Integer operatorRole) {

        validateAdminRole(operatorRole);

        if (dto == null) {
            throw new IllegalArgumentException("流量阻断命令参数不能为空");
        }

        return managedDeviceCommandService.enqueueBlockTraffic(deviceCode, dto.getDstIp(), dto.getSni(), null, DeviceCommandPurpose.MANUAL_BLOCK_TRAFFIC);
    }

    private void validateAdminRole(Integer operatorRole) {
        boolean allowed = Integer.valueOf(SUPER_ADMIN_ROLE).equals(operatorRole) || Integer.valueOf(ADMIN_ROLE).equals(operatorRole);

        if (!allowed) {
            throw new IllegalArgumentException("当前用户没有设备控制权限");
        }
    }
}