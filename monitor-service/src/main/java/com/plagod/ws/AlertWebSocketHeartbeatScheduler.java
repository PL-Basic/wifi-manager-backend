package com.plagod.ws;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertWebSocketHeartbeatScheduler {

    private final AlertWebSocketHandler alertWebSocketHandler;

    public AlertWebSocketHeartbeatScheduler(AlertWebSocketHandler alertWebSocketHandler) {
        this.alertWebSocketHandler = alertWebSocketHandler;
    }

    @Scheduled(fixedDelayString = "${wifi.websocket.heartbeat-interval-millis:25000}")
    public void heartbeat() {
        alertWebSocketHandler.sendHeartbeat();
    }
}
