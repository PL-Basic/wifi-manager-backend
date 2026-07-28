package com.plagod.dto.device;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class PortalAuthorizeDTO {

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码长度不能超过 64")
    private String deviceCode;


    @NotBlank(message = "MAC 地址不能为空")
    @Pattern(regexp = "(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$", message = "MAC 地址格式不正确")
    private String mac;

    @NotBlank(message = "IP 地址不能为空")
    @Size(max = 45, message = "IP地址不能超过45")
    private String ip;

    @Size(max = 255, message = "设备信息长度不能超过255")
    private String deviceInfo;

    // 达到连接上限时，是否明确替换最旧 Session。
    private Boolean forceReplaceOldest = false;
}