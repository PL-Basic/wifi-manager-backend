package com.plagod.vo.tenant;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SaasPlanVO {
    private String planId;
    private String planCode;
    private String name;
    private String status;
    private String currentPublishedVersionId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
