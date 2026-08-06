package com.plagod.vo.auth;

import lombok.Data;

@Data
public class OperationTokenConsumptionVO {

    private String userId;
    private String purpose;
    private String businessKey;
}
