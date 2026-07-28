package com.plagod.dto.device;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class ManualBlockTrafficDTO {

    @NotBlank(message = "目标 IP 不能为空")
    @Size(max = 15, message = "目标 IPv4 长度不能超过 15")
    @Pattern(
            regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$",
            message = "目标 IP 必须是合法 IPv4 地址")
    private String dstIp;

    @Size(max = 255, message = "SNI 长度不能超过 255")
    private String sni;
}
