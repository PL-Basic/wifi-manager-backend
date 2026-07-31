package com.plagod.sender.phone;

import com.plagod.configuration.VerificationCodeProperties;
import com.plagod.sender.ConsoleVerifyCodeSender;
import com.plagod.utils.PasswordUtils;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class LocalPhoneVerificationProvider implements PhoneVerificationProvider {

    private final VerificationCodeProperties properties;
    private final ConsoleVerifyCodeSender consoleSender;
    private final SecureRandom random = new SecureRandom();

    public LocalPhoneVerificationProvider(VerificationCodeProperties properties, ConsoleVerifyCodeSender consoleSender) {

        this.properties = properties;
        this.consoleSender = consoleSender;
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public PhoneVerificationSendResult send(String phone, String scene, String outId) {

        String code = generateCode();

        consoleSender.send(phone, "phone", scene, code);

        return PhoneVerificationSendResult.builder()
                .successful(true)
                .provider(providerName())
                .outId(outId)
                .requestId(outId)
                .providerCode("LOCAL_OK")
                .message("本地验证码已生成")
                .localCodeHash(PasswordUtils.encode(code))
                .build();
    }

    @Override
    public PhoneVerificationCheckResult verify(String phone, String outId, String submittedCode, String storedCodeHash) {

        boolean matched = storedCodeHash != null && PasswordUtils.matches(submittedCode, storedCodeHash);

        return PhoneVerificationCheckResult.builder()
                .requestSuccessful(true)
                .verified(matched)
                .provider(providerName())
                .outId(outId)
                .providerCode("LOCAL_OK")
                .providerResult(matched ? "PASS" : "UNKNOWN")
                .message(matched ? "验证码核验成功" : "验证码错误")
                .build();
    }

    private String generateCode() {
        String chars = properties.getCodeChars();
        int length = properties.getCodeLength();

        if (chars == null || chars.isEmpty() || length < 1) {
            throw new IllegalStateException("本地验证码生成配置无效");
        }

        StringBuilder result = new StringBuilder(length);

        for (int index = 0; index < length; index++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }

        return result.toString();
    }
}