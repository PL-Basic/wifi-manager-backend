package com.plagod.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountSwitchCodeVO {

    private final String channel;
    private final String maskedTarget;
}
