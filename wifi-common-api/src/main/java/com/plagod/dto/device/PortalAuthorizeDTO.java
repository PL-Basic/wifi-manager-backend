package com.plagod.dto.device;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Portal 认证请求 DTO。
 * 手机连上 ESP32 开放热点后，Portal 页面提交此表单到后端。
 * 放在 common-api 是因为 admin-service（Feign）和 device-service（Controller）都需要这个类型。
 */
@Data
public class PortalAuthorizeDTO {

    /** 用户所连接的 ESP32 设备编码，必填 */
    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    /** 用户手机 WiFi MAC 地址，必填，格式如 AA:BB:CC:DD:EE:FF */
    @NotBlank(message = "MAC 地址不能为空")
    private String mac;

    /** 用户手机获取的 IP 地址，必填，用于关联会话 */
    @NotBlank(message = "IP 地址不能为空")
    private String ip;

    /** 可选的设备描述信息，例如浏览器的 User-Agent */
    private String deviceInfo;
}
