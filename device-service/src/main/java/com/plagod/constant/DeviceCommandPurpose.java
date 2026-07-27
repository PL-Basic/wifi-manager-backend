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

    private DeviceCommandPurpose() {
    }

    public static boolean isSessionRevokePurpose(String purpose) {
        return USER_LOGOUT.equals(purpose) || ADMIN_REVOKE.equals(purpose);
    }

    // 只有这两种 ALLOW 命令可以驱动 Session 授权状态。
    public static boolean isSessionAllowPurpose(String purpose) {
        return PORTAL_AUTHORIZE.equals(purpose) || LEASE_RENEW.equals(purpose);
    }
}