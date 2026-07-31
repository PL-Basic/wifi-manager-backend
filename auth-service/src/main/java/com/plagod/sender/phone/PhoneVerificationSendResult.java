package com.plagod.sender.phone;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PhoneVerificationSendResult {

    boolean successful;
    String provider;
    String outId;
    String requestId;
    String bizId;
    String providerCode;
    String message;

    /**
     * 仅 LOCAL 模式返回 BCrypt 摘要，阿里云模式必须为空。
     */
    String localCodeHash;
}