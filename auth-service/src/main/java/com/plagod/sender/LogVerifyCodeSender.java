package com.plagod.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogVerifyCodeSender implements VerifyCodeSender {
    @Override
    public void send(String target, String targetType, String scene, String code) {
        log.info("验证码发送模拟：target{},targetType{},scene{},code{}", target, targetType, scene, code );
    }
}
