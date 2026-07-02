package com.plagod.dto.user;

import lombok.Data;

@Data
public class UserOperationReviewDTO {
    private Boolean approved;
    private String rejectReason;
}
