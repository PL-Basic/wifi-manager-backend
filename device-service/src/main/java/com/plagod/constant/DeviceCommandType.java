package com.plagod.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DeviceCommandType {

    public static final String ALLOW = "ALLOW";
    public static final String REVOKE_ACCESS = "REVOKE_ACCESS";
    public static final String KICK = "KICK";
    public static final String DISCONNECT_MAC = "DISCONNECT_MAC";
    public static final String BLOCK_TRAFFIC = "BLOCK_TRAFFIC";
    public static final String STAGE_WIFI_CONFIG = "STAGE_WIFI_CONFIG";

    private static final Set<String> TERMINAL_TYPES =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                    ALLOW,
                    REVOKE_ACCESS,
                    KICK,
                    DISCONNECT_MAC,
                    BLOCK_TRAFFIC,
                    STAGE_WIFI_CONFIG
            )));

    private DeviceCommandType() {
    }

    public static Set<String> terminalTypes() {
        return TERMINAL_TYPES;
    }

    public static boolean isTerminalType(String commandType) {
        return TERMINAL_TYPES.contains(commandType);
    }

    public static boolean isSensitiveType(String commandType) {
        return STAGE_WIFI_CONFIG.equals(commandType);
    }
}
