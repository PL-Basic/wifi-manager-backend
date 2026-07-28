package com.plagod.vo.device;

import lombok.Data;

import java.util.List;

@Data
public class DeviceCommandPageResult {

    private long total;
    private long current;
    private long size;
    private List<DeviceCommandVO> records;
}