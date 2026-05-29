package com.plagod.service;

public interface VerificationCodeService {
    //发送验证码
    void sendCode(String target,String scene,String sendIp);
    //校验验证码
    void verifyAndConsume(String target,String scene,String code,String verifyIp);
    //检查验证码
    void checkCode(String target,String scene,String code);
    //消费验证码
    void consumeCode(String target,String scene,String code,String consumeIp);

}
