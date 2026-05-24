package com.plagod.enums;

import lombok.Getter;

@Getter
public enum ConflictFieldEnum {
    USERNAME("用户名"),
    EMAIL("邮箱"),
    PHONE("手机号");

    private final String displayName;

    ConflictFieldEnum(String displayName) {
        this.displayName = displayName;
    }

}
