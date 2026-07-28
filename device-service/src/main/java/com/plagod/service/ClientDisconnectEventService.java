package com.plagod.service;

import com.plagod.dto.ClientDisconnectEvent;

public interface ClientDisconnectEventService {

    // 处理 ESP32 上报的客户端物理断线事件。
    void handleClientDisconnectEvent(ClientDisconnectEvent event);
}
