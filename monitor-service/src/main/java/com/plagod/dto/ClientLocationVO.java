package com.plagod.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ClientLocationVO {
    private Long id;
    private String mac;
    private Long userId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracy;
    private LocalDateTime consentTime;
    private LocalDateTime reportTime;
    private String source;
    private String remark;
    private LocalDateTime createTime;
}
