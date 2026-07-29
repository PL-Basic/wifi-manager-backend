package com.plagod.service;

import com.plagod.entity.device.DeviceCommandRecord;

public interface SessionCommandLifecycleService {

    // 使用命令最终执行状态驱动关联 Session。
    void handleTerminalCommand(DeviceCommandRecord command);
}