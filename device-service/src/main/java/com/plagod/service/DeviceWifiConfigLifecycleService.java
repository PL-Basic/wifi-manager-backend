package com.plagod.service;

import com.plagod.dto.DeviceStatusEvent;
import com.plagod.entity.DeviceCommandRecord;
import com.plagod.entity.Esp32Node;

import java.time.LocalDateTime;

public interface DeviceWifiConfigLifecycleService {

    void handleTerminalCommand(DeviceCommandRecord command);

    void handleStatusEvent(Esp32Node node, DeviceStatusEvent event, LocalDateTime heartbeatTime);
}