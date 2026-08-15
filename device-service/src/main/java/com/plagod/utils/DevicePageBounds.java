package com.plagod.utils;

import com.plagod.support.PageBounds;

/**
 * 将 device-service 现有 long 分页参数适配到公共 PageBounds。
 */
public final class DevicePageBounds {

    private DevicePageBounds() {
    }

    public static PageBounds normalize(long current, long size) {
        return PageBounds.of(saturatedInteger(current), saturatedInteger(size));
    }

    private static int saturatedInteger(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
