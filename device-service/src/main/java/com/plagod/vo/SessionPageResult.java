package com.plagod.vo;

import lombok.Data;

import java.util.List;

@Data
public class SessionPageResult {
    private long total;
    private long current;
    private long size;
    private List<SessionRecordVO> records;
}
