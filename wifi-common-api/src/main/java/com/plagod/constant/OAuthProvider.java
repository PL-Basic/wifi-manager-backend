package com.plagod.constant;

import java.util.Locale;

public enum OAuthProvider {

    GITHUB,
    QQ,
    WECHAT;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OAuthProvider parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("OAuth Provider不能为空");
        }

        try {
            return OAuthProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("OAuth Provider只支持github、qq和wechat");
        }
    }
}