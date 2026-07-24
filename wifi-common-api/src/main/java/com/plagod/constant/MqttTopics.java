package com.plagod.constant;

public class MqttTopics {

    private MqttTopics() {
    }

    // /cmd/... 是 后端下发命令给 ESP32。
    public static final String DEVICE_ALLOW = "wifi/device/%s/cmd/allow";
    public static final String DEVICE_KICK = "wifi/device/%s/cmd/kick";
    public static final String DEVICE_DISCONNECT_MAC = "wifi/device/%s/cmd/disconnect-mac";
    public static final String DEVICE_BLOCK_TRAFFIC = "wifi/device/%s/cmd/block-traffic";
    public static final String DEVICE_REVOKE_ACCESS = "wifi/device/%s/cmd/revoke-access";

    // /event/status 是 ESP32 主动上报事件给后端。
    public static final String DEVICE_STATUS = "wifi/device/%s/event/status";
    public static final String DEVICE_TRAFFIC = "wifi/device/%s/event/traffic";
    public static final String DEVICE_CLIENT_SIGNAL = "wifi/device/%s/event/client-signal";
    public static final String DEVICE_COMMAND_RESULT = "wifi/device/%s/event/command-result";

    // ---- ESP32 → 后端上报命令执行结果 ----
    public static final String DEVICE_STATUS_SUBSCRIBE = "wifi/device/+/event/status";
    public static final String DEVICE_TRAFFIC_SUBSCRIBE = "wifi/device/+/event/traffic";
    public static final String DEVICE_COMMAND_RESULT_SUBSCRIBE = "wifi/device/+/event/command-result";
    public static final String DEVICE_CLIENT_SIGNAL_SUBSCRIBE = "wifi/device/+/event/client-signal";


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

    public static String deviceCommandResult(String deviceCode) {
        return String.format(DEVICE_COMMAND_RESULT, deviceCode);
    }

    public static String deviceRevokeAccess(String deviceCode) {
        return String.format(DEVICE_REVOKE_ACCESS, deviceCode);
    }

    public static String deviceClientSignal(String deviceCode) {
        return String.format(DEVICE_CLIENT_SIGNAL, deviceCode);
    }
}
