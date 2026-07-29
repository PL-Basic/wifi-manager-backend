package com.plagod.vo.entitlement;

import lombok.Data;

import java.util.List;

@Data
public class EntitlementOrderPageResult {

    private long total;
    private long current;
    private long size;
    private List<EntitlementOrderVO> records;
}