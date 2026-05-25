package com.plagod.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClientLocationPageResult {
    private long total;
    private long current;
    private long size;
    private List<ClientLocationVO> records;
}
