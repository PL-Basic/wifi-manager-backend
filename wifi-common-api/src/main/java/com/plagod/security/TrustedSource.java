package com.plagod.security;

/**
 * 可信身份的来源类型；来源与用户工作区上下文是两个独立维度。
 */
public enum TrustedSource {
    GATEWAY_USER,
    INTERNAL_SERVICE,
    SCHEDULED_SERVICE,
    DEVICE_EVENT
}
