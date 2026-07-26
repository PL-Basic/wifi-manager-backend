package com.plagod.constant;

public final class SessionStatus {

    // Session 已经结束，不再占用连接名额。
    public static final int CLOSED = 0;

    // ESP32 已经明确返回 ALLOW 执行成功。
    public static final int ACTIVE = 1;

    // ALLOW 已经入队，但还没有收到 ESP32 的成功结果。
    public static final int PENDING = 2;

    private SessionStatus() {
    }

    public static boolean isActive(Integer status) {
        return Integer.valueOf(ACTIVE).equals(status);
    }

    public static boolean isPending(Integer status) {
        return Integer.valueOf(PENDING).equals(status);
    }

    // ACTIVE 和 PENDING 都是尚未结束的开放会话。
    public static boolean isOpen(Integer status) {
        return isActive(status) || isPending(status);
    }
}