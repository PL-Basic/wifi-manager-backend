package com.plagod.dto;

import com.plagod.entity.UserOperationRequest;
import lombok.Data;

import java.util.List;

@Data
public class UserOperationRequestPageResult {
    private long total;
    private long current;
    private long size;
    private List<UserOperationRequest> records;
}
