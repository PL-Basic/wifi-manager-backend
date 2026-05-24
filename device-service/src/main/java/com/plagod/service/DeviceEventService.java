package com.plagod.service;

import com.plagod.dto.DeviceStatusEvent;

public interface DeviceEventService {
    void handleStatusEvent(DeviceStatusEvent event);
}
