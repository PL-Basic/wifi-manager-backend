package com.plagod.sender.phone;

public interface PhoneVerificationProvider {

    String providerName();

    PhoneVerificationSendResult send(String phone, String scene, String outId);

    PhoneVerificationCheckResult verify(
            String phone,
            String outId,
            String submittedCode,
            String storedCodeHash
    );
}