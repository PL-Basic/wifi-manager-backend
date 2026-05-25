package com.plagod.constant;

public class MqttTopics {

    private MqttTopics() {
    }

    public static final String DEVICE_ALLOW = "wifi/device/%s/cmd/allow";
    public static final String DEVICE_KICK = "wifi/device/%s/cmd/kick";
    public static final String DEVICE_DISCONNECT_MAC = "wifi/device/%s/cmd/disconnect-mac";
    public static final String DEVICE_BLOCK_TRAFFIC = "wifi/device/%s/cmd/block-traffic";
    public static final String DEVICE_STATUS = "wifi/device/%s/event/status";
    public static final String DEVICE_TRAFFIC = "wifi/device/%s/event/traffic";
    public static final String DEVICE_STATUS_SUBSCRIBE = "wifi/device/+/event/status";
    public static final String DEVICE_TRAFFIC_SUBSCRIBE = "wifi/device/+/event/traffic";

    public static String deviceAllow(String deviceCode) {
        return String.format(DEVICE_ALLOW, deviceCode);
    }

    public static String deviceKick(String deviceCode) {
        return String.format(DEVICE_KICK, deviceCode);
    }

    public static String deviceDisconnectMac(String deviceCode) {
        return String.format(DEVICE_DISCONNECT_MAC, deviceCode);
    }

    public static String deviceBlockTraffic(String deviceCode) {
        return String.format(DEVICE_BLOCK_TRAFFIC, deviceCode);
    }
}
