package com.plagod.dto.device;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class WifiConfigStageDTO {

    @NotNull(message = "SSID 不能为空")
    @Size(max = 32, message = "SSID 字符数不能超过 32")
    private String ssid;

    // 空字符串表示开放网络；WRITE_ONLY 防止被响应序列化。
    @NotNull(message = "password 字段不能为空")
    @Size(max = 63, message = "WiFi 密码字符数不能超过 63")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
}