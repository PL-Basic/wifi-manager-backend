package com.plagod.service;

public interface VerificationCodeService {
    //发送验证码
    void sendCode(String target,String scene,String sendIp);
    //校验验证码
    void verifyCode(String target,String scene,String code,String verifyIp);
}
