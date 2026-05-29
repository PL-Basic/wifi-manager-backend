package com.plagod.service;

public interface VerificationCodeService {
    //发送验证码
    void sendCode(String target,String scene,String sendIp);
    //检查验证码
    void checkCode(String target,String scene,String code);
    //消费验证码
    void consumeCode(String target,String scene,String code,String consumeIp);

}
