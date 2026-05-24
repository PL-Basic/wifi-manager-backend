package com.plagod.dto;

import lombok.Data;

import java.util.List;

@Data
public class MacBlacklistPageResult {
    private long total;
    private long current;
    private long size;
    private List<MacBlacklistVO> records;
}
