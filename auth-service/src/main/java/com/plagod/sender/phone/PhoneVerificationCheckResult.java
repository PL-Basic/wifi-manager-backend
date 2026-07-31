package com.plagod.sender.phone;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PhoneVerificationCheckResult {

    /**
     * 表示 Provider API 是否正常完成，不代表验证码正确。
     */
    boolean requestSuccessful;

    boolean verified;
    String provider;
    String outId;
    String providerCode;
    String providerResult;
    String message;
}