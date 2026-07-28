package com.plagod.constant;

public final class DeviceCommandPurpose {

    // Portal 首次认证或者重复认证产生的 ALLOW。
    public static final String PORTAL_AUTHORIZE = "PORTAL_AUTHORIZE";

    // ACTIVE Session 定时续租产生的 ALLOW。
    public static final String LEASE_RENEW = "LEASE_RENEW";

    // 用户主动退出认证产生的撤销命令。
    public static final String USER_LOGOUT = "USER_LOGOUT";

    // 管理员手动撤销 Session 产生的撤销命令。
    public static final String ADMIN_REVOKE = "ADMIN_REVOKE";

    // 强制登录替换最旧 Session 产生的撤销命令。
    public static final String FORCE_LOGIN_REPLACE = "FORCE_LOGIN_REPLACE";

    // 新增黑名单后撤销客户端固件授权。
    public static final String BLACKLIST_REVOKE = "BLACKLIST_REVOKE";

    // Portal 认证替换同一 MAC 的历史 Session 时撤销旧授权。
    public static final String PORTAL_CONFLICT_REVOKE = "PORTAL_CONFLICT_REVOKE";

    public static final String MONITOR_AUTO_DISCONNECT = "MONITOR_AUTO_DISCONNECT";

    public static final String MONITOR_AUTO_BLOCK_TRAFFIC = "MONITOR_AUTO_BLOCK_TRAFFIC";

    public static final String MANUAL_DISCONNECT = "MANUAL_DISCONNECT";

    public static final String MANUAL_BLOCK_TRAFFIC = "MANUAL_BLOCK_TRAFFIC";

    // 管理员要求 ESP32 节点执行安全重启。
    public static final String MANUAL_DEVICE_RESTART = "MANUAL_DEVICE_RESTART";

    private DeviceCommandPurpose() {
    }

    public static boolean isSessionRevokePurpose(String purpose) {
        return USER_LOGOUT.equals(purpose)
                || ADMIN_REVOKE.equals(purpose)
                || FORCE_LOGIN_REPLACE.equals(purpose)
                || BLACKLIST_REVOKE.equals(purpose)
                || PORTAL_CONFLICT_REVOKE.equals(purpose);
    }

    // 只有这两种 ALLOW 命令可以驱动 Session 授权状态。
    public static boolean isSessionAllowPurpose(String purpose) {
        return PORTAL_AUTHORIZE.equals(purpose) || LEASE_RENEW.equals(purpose);
    }

    public static boolean isDisconnectMacPurpose(String purpose) {
        return MONITOR_AUTO_DISCONNECT.equals(purpose) || MANUAL_DISCONNECT.equals(purpose);
    }

    public static boolean isBlockTrafficPurpose(String purpose) {
        return MONITOR_AUTO_BLOCK_TRAFFIC.equals(purpose) || MANUAL_BLOCK_TRAFFIC.equals(purpose);
    }

    public static boolean isKickPurpose(String purpose) {
        return MANUAL_DEVICE_RESTART.equals(purpose);
    }
}
