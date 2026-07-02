package com.plagod.vo.monitor;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogVO {
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String target;
    private String detail;
    private String ip;
    private LocalDateTime createTime;
}
