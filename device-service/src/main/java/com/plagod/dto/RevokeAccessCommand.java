package com.plagod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RevokeAccessCommand {

    // 用于关联 ESP32 返回的 command-result。
    private String requestId;

    // 需要撤销认证的客户端 MAC。
    private String mac;

    // 防止旧撤销命令影响客户端后来建立的新 Session。
    private Long sessionId;
}