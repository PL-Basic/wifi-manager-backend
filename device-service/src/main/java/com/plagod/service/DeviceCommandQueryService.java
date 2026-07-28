package com.plagod.service;

import com.plagod.vo.device.DeviceCommandPageResult;

public interface DeviceCommandQueryService {

    DeviceCommandPageResult pageCommands(long current, long size, String requestId, String deviceCode, String commandType, String purpose, Integer status, Long sessionId, String mac);
}