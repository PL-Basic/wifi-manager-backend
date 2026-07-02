package com.plagod.vo.user;

import lombok.Data;

import java.util.List;

@Data
public class UserOperationRequestPageResult {
    private long total;
    private long current;
    private long size;
    private List<UserOperationRequestVO> records;
}
