package com.plagod.constant;

import java.util.Locale;

public enum OAuthPurpose {

    LOGIN,
    BIND;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OAuthPurpose parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("OAuth用途不能为空");
        }

        try {
            return OAuthPurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("OAuth用途只支持login或bind");
        }
    }
}