package com.plagod.sender;

import com.plagod.entity.VerifyCode;
import org.springframework.stereotype.Component;

//终端处理，做测试作用
@Component
public class ConsoleVerifyCodeSender {

    public void send(String target, String targetType, String scene, String code) {
        System.out.println("verify code target = " + target
                + ",targetType = " + targetType
                + ",scene = " + scene
                + ",code = " + code);
    }

}
