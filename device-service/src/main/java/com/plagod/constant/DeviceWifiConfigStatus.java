package com.plagod.constant;

public final class DeviceWifiConfigStatus {

    public static final int DISPATCHING = 0;
    public static final int STAGED = 1;
    public static final int ACTIVE = 2;
    public static final int FAILED = 3;
    public static final int UNKNOWN = 4;
    public static final int SUPERSEDED = 5;

    private DeviceWifiConfigStatus() {
    }

    public static boolean isKnown(Integer status) {
        return status != null && status >= DISPATCHING && status <= SUPERSEDED;
    }

    public static boolean isReplaceable(Integer status) {
        return Integer.valueOf(STAGED).equals(status) || Integer.valueOf(UNKNOWN).equals(status);
    }

    public static boolean isTerminal(int status) {
        return status == ACTIVE || status == FAILED || status == SUPERSEDED;
    }

    public static String nameOf(Integer status) {
        if (status == null) {
            return "INVALID";
        }

        switch (status) {
            case DISPATCHING:
                return "DISPATCHING";
            case STAGED:
                return "STAGED";
            case ACTIVE:
                return "ACTIVE";
            case FAILED:
                return "FAILED";
            case UNKNOWN:
                return "UNKNOWN";
            case SUPERSEDED:
                return "SUPERSEDED";
            default:
                return "INVALID";
        }
    }
}