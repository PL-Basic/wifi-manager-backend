package com.plagod.vo.tenant;

import lombok.Data;

import java.util.List;

@Data
public class TenantPageResult {
    private long total;
    private long current;
    private long size;
    private List<TenantVO> records;
}
