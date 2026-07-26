package com.plagod.constant;

public final class DeviceCommandPurpose {

    // Portal 首次认证或者重复认证产生的 ALLOW。
    public static final String PORTAL_AUTHORIZE = "PORTAL_AUTHORIZE";

    // ACTIVE Session 定时续租产生的 ALLOW。
    public static final String LEASE_RENEW = "LEASE_RENEW";

    private DeviceCommandPurpose() {
    }

    // 只有这两种 ALLOW 命令可以驱动 Session 授权状态。
    public static boolean isSessionAllowPurpose(String purpose) {
        return PORTAL_AUTHORIZE.equals(purpose) || LEASE_RENEW.equals(purpose);
    }
}