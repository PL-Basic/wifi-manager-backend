package com.plagod.constant;

public final class SessionStatus {

    // Session 已经结束，不再占用连接名额。
    public static final int CLOSED = 0;

    // ESP32 已经明确返回 ALLOW 执行成功。
    public static final int ACTIVE = 1;

    // ALLOW 已经入队，但还没有收到 ESP32 的成功结果。
    public static final int PENDING = 2;

    // 正在等待最旧 Session 的 REVOKE_ACCESS 结果。
    public static final int WAITING_REPLACEMENT = 3;

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

    public static boolean isWaitingReplacement(Integer status) {
        return Integer.valueOf(WAITING_REPLACEMENT).equals(status);
    }

    // 已占用用户连接名额，但不一定已获得固件授权。
    public static boolean isAllocated(Integer status) {
        return isOpen(status) || isWaitingReplacement(status);
    }
}