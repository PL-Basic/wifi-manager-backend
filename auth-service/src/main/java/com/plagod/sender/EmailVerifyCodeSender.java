package com.plagod.sender;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailVerifyCodeSender {

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username:}")
    private String from;


    public void send(String target, String scene, String code){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(target);
        message.setSubject(buildSubject(scene));
        message.setText(buildContent(scene, code));

        javaMailSender.send(message);
    }

    private String buildSubject(String scene){
        if("login".equals(scene)){
            return "登录验证码";
        }
        if("register".equals(scene)){
            return "注册验证码";
        }
        if ("reset_password".equals(scene)){
            return "重置密码验证码";
        }
        if ("bind_contact".equals(scene)){
            return "绑定邮箱验证码";
        }

        return "验证码";
    }

    private String buildContent(String scene, String code){
        return "您的验证码是：" + code + "\n"
                + "验证码用于：" + buildSubject(scene) + "\n"
                + "请在有效期间内完成验证。"
                + "\n如果不是您本人操作，请忽略此邮件。";
    }

}
