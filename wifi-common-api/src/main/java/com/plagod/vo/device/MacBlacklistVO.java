package com.plagod.vo.device;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MacBlacklistVO {
    private Long id;
    private String mac;
    private String reason;
    private Long operatorId;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
