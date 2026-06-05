package com.plagod.sender;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefaultVerifyCodeSender implements VerifyCodeSender {
    @Autowired
    private EmailVerifyCodeSender emailVerifyCodeSender;

    @Autowired
    private ConsoleVerifyCodeSender consoleVerifyCodeSender;

    @Override
    public void send(String target, String targetType, String scene, String code) {
        if ("email".equals(targetType)) {
            emailVerifyCodeSender.send(target, scene, code);
            return;
        }

        consoleVerifyCodeSender.send(target,targetType,scene,code);
    }
}
