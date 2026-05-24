package com.plagod.service;

import com.plagod.dto.DeviceTrafficEvent;

public interface TrafficEventService {
    void handleTrafficEvent(DeviceTrafficEvent event);
}
