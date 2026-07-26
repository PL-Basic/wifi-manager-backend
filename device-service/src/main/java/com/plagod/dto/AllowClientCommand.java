package com.plagod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AllowClientCommand {
    // 用于关联 ESP32 返回的 command-result。
    private String requestId;
    // 被授权客户端的规范化 MAC。
    private String mac;
    // 后端已经落库的有效会话编号。
    private Long sessionId;
    // ESP32 保存授权状态的有效秒数。
    private Integer ttlSeconds;
}
