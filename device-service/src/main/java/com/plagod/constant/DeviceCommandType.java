package com.plagod.constant;

public final class DeviceCommandType {

    public static final String STAGE_WIFI_CONFIG = "STAGE_WIFI_CONFIG";

    private DeviceCommandType() {
    }

    public static boolean isSensitiveType(String commandType) {
        return STAGE_WIFI_CONFIG.equals(commandType);
    }
}