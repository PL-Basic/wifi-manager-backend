package com.plagod.service;

import com.plagod.dto.device.*;
import com.plagod.vo.device.*;

public interface DeviceCommandService {
    DeviceNodeVO getDevice(Long nodeId);

    DeviceCommandResult allowDevice(String deviceCode);

    DeviceCommandResult kickDevice(String deviceCode, KickDeviceDTO kickDeviceDTO);

    void addBlacklist(MacBlacklistCreateDTO createDTO);

    void removeBlacklist(String mac);

    DeviceStatsVO getDeviceStats();

    DevicePageResult pageDevices(long current, long size, String keyword);

    MacBlacklistPageResult pageBlacklist(long current, long size, String keyword);

    DeviceNodeVO createDevice(DeviceNodeCreateDTO createDTO);

    DeviceNodeVO updateDevice(Long nodeId, DeviceNodeUpdateDTO updateDTO);

    void deleteDevice(Long nodeId);

    DeviceNodeVO restoreDevice(Long nodeId);
}
