package com.plagod.dto.device;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class ManualDisconnectMacDTO {

    @NotBlank(message = "MAC 地址不能为空")
    @Pattern(regexp = "(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$", message = "MAC 地址格式不正确")
    private String mac;
}