package com.plagod.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Component
public class WifiCommandPayloadCrypto {

    private static final String ENVELOPE_VERSION = "v1";
    private static final String CIPHER_NAME = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${wifi.command.secret-key:}")
    private String encodedSecretKey;

    private SecretKeySpec secretKey;

    @PostConstruct
    public void initialize() {
        if (!StringUtils.hasText(encodedSecretKey)) {
            // 缺少密钥只禁用敏感命令，不阻断整个 device-service。
            log.warn("未配置 WIFI_COMMAND_SECRET_KEY，候选 WiFi 配置功能暂不可用");
            return;
        }

        byte[] keyBytes = null;

        try {
            keyBytes = Base64.getDecoder().decode(encodedSecretKey.trim());

            if (keyBytes.length != KEY_LENGTH_BYTES) {
                log.warn("WIFI_COMMAND_SECRET_KEY 解码后不是 32 字节，候选 WiFi 配置功能暂不可用");
                return;
            }

            secretKey = new SecretKeySpec(keyBytes, "AES");

        } catch (IllegalArgumentException exception) {
            log.warn("WIFI_COMMAND_SECRET_KEY 不是合法 Base64，候选 WiFi 配置功能暂不可用");
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    public boolean isAvailable() {
        return secretKey != null;
    }
    /**
     * requestId 作为 AAD，防止数据库中的密文被挪到另一条命令使用。
     */
    public String encrypt(String plaintext, String requestId) {
        validateInput(plaintext, requestId);

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(requestId.getBytes(StandardCharsets.UTF_8));

            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            return ENVELOPE_VERSION
                    + ":"
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("敏感命令载荷加密失败");
        } finally {
            Arrays.fill(plaintextBytes, (byte) 0);
        }
    }

    public String decrypt(String envelope, String requestId) {
        validateInput(envelope, requestId);

        String[] parts = envelope.split(":", -1);

        if (parts.length != 3 || !ENVELOPE_VERSION.equals(parts[0])) {
            throw new IllegalStateException("敏感命令载荷格式无效");
        }

        byte[] iv;
        byte[] ciphertext;

        try {
            iv = Base64.getDecoder().decode(parts[1]);
            ciphertext = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("敏感命令载荷格式无效");
        }

        if (iv.length != IV_LENGTH_BYTES || ciphertext.length == 0) {
            throw new IllegalStateException("敏感命令载荷格式无效");
        }

        byte[] plaintextBytes = null;

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(requestId.getBytes(StandardCharsets.UTF_8));

            plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            // 不向日志或调用方暴露密钥、密文和解密细节。
            throw new IllegalStateException("敏感命令载荷解密失败");
        } finally {
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);

            if (plaintextBytes != null) {
                Arrays.fill(plaintextBytes, (byte) 0);
            }
        }
    }

    private void validateInput(String value, String requestId) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("敏感命令载荷和 requestId 不能为空");
        }

        if (secretKey == null) {
            throw new IllegalStateException("敏感设备命令密钥未配置，暂时不能执行候选 WiFi 配置");
        }
    }
}