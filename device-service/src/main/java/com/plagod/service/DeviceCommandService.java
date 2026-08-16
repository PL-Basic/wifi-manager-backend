package com.plagod.service;

import com.plagod.dto.device.*;
import com.plagod.vo.device.*;

public interface DeviceCommandService {
    DeviceNodeVO getDevice(Long tenantId, Long nodeId);

    DeviceNodeVO allowDevice(Long tenantId, String deviceCode);

    DeviceCommandResult kickDevice(Long tenantId, String deviceCode, KickDeviceDTO kickDeviceDTO);

    void removeBlacklist(Long tenantId, String mac);

    DeviceStatsVO getDeviceStats(Long tenantId);

    DevicePageResult pageDevices(Long tenantId, long current, long size, String keyword);

    MacBlacklistPageResult pageBlacklist(Long tenantId, long current, long size, String keyword);

    DeviceNodeVO createDevice(Long tenantId, DeviceNodeCreateDTO createDTO);

    DeviceNodeVO updateDevice(Long tenantId, Long nodeId, DeviceNodeUpdateDTO updateDTO);

    void deleteDevice(Long tenantId, Long nodeId);

    DeviceNodeVO restoreDevice(Long tenantId, Long nodeId);

    DeviceCommandResult allowClient(Long nodeId, String deviceCode, String mac, Long sessionId, Integer ttlSeconds);

    DeviceCommandResult refreshClientLease(Long nodeId, String deviceCode, String mac, Long sessionId, Integer ttlSeconds);

    DeviceCommandResult revokeClientAccess(Long nodeId, String deviceCode, String mac, Long sessionId, String purpose);
}
