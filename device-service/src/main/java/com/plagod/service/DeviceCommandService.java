package com.plagod.service;

import com.plagod.dto.DeviceCommandResult;
import com.plagod.dto.DeviceNodeVO;
import com.plagod.dto.DevicePageResult;
import com.plagod.dto.DeviceStatsVO;
import com.plagod.dto.KickDeviceDTO;
import com.plagod.dto.MacBlacklistCreateDTO;
import com.plagod.dto.MacBlacklistPageResult;

public interface DeviceCommandService {
    DeviceNodeVO getDevice(Long nodeId);

    DeviceCommandResult allowDevice(String deviceCode);

    DeviceCommandResult kickDevice(String deviceCode, KickDeviceDTO kickDeviceDTO);

    void addBlacklist(MacBlacklistCreateDTO createDTO);

    void removeBlacklist(String mac);

    DeviceStatsVO getDeviceStats();

    DevicePageResult pageDevices(long current, long size, String keyword);

    MacBlacklistPageResult pageBlacklist(long current, long size, String keyword);
}
