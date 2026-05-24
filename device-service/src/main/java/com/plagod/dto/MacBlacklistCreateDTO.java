package com.plagod.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Data
public class MacBlacklistCreateDTO {
    @NotBlank(message = "MAC 地址不能为空")
    private String mac;

    private String reason;
    private Long operatorId;
    private LocalDateTime expireTime;
}
