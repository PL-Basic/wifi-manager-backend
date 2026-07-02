package com.plagod.vo.monitor;

import lombok.Data;

import java.util.List;

@Data
public class AuditLogPageResult {
    private long total;
    private long current;
    private long size;
    private List<AuditLogVO> records;
}
