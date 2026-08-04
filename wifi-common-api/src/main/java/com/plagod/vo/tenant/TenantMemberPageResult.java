package com.plagod.vo.tenant;

import lombok.Data;

import java.util.List;

@Data
public class TenantMemberPageResult {
    private long total;
    private long current;
    private long size;
    private List<TenantMemberVO> records;
}
