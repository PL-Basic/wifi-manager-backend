package com.plagod.dto;

import lombok.Data;

@Data
public class ClientDisconnectEvent {

    // payload 可携带，但后端最终使用 MQTT topic 中的设备编码。
    private String deviceCode;

    // 断开连接的客户端 MAC。
    private String mac;

    // 0 表示客户端尚未认证，正数表示后端 Session。
    private Long sessionId;

    // 固件删除状态前保存的状态，例如 AUTHORIZED。
    private String state;
}