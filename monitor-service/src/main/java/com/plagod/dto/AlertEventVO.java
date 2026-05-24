package com.plagod.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertEventVO {
    private Long id;
    private Integer level;
    private String ruleCode;
    private String title;
    private String mac;
    private Long userId;
    private String detail;
    private Integer status;
    private Long handleUserId;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
