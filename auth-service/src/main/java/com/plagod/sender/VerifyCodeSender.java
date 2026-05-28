package com.plagod.sender;

public interface VerifyCodeSender {
    void send(String target,String targetType,String scene,String code);
}
