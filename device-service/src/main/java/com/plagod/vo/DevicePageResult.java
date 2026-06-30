package com.plagod.vo;

import lombok.Data;

import java.util.List;

@Data
public class DevicePageResult {
    private long total;
    private long current;
    private long size;
    private List<DeviceNodeVO> records;
}
