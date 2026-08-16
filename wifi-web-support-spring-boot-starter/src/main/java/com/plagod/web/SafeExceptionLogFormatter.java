package com.plagod.web;

/**
 * 保留既有调用方的兼容入口，唯一实现位于框架无关的 common 模块。
 */
@Deprecated
public final class SafeExceptionLogFormatter {

    private SafeExceptionLogFormatter() {
    }

    public static String format(Throwable throwable) {
        return com.plagod.support.SafeExceptionLogFormatter.format(
                throwable);
    }
}
