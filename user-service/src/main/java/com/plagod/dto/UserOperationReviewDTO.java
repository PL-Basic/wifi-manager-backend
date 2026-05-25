package com.plagod.dto;

import lombok.Data;

@Data
public class UserOperationReviewDTO {
    private Boolean approved;
    private String rejectReason;
}
