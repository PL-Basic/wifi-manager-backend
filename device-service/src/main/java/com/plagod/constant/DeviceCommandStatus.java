package com.plagod.constant;

public final class DeviceCommandStatus {
    // 已经落库，等待 MQTT 发布。
    public static final int PENDING = 0;

    // MQTT Broker 已接收，等待 ESP32 command-result。
    public static final int PUBLISHED = 1;

    // ESP32 明确返回执行成功。
    public static final int SUCCEEDED = 2;

    // ESP32 明确返回执行失败。
    public static final int EXECUTION_FAILED = 3;

    // 多次尝试后仍无法发布到 MQTT Broker。
    public static final int PUBLISH_FAILED = 4;

    // 已发布，但规定时间内没有收到 ESP32 结果。
    public static final int TIMED_OUT = 5;

    private DeviceCommandStatus() {
    }

    public static boolean isTerminal(Integer status) {
        return Integer.valueOf(SUCCEEDED).equals(status) || Integer.valueOf(EXECUTION_FAILED).equals(status) || Integer.valueOf(PUBLISH_FAILED).equals(status) || Integer.valueOf(TIMED_OUT).equals(status);
    }
}