package com.plagod.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserOperationRequestVO {
    private Long id;

    private String requestType;

    private Long targetUserId;

    private String targetUsername;

    private Long requesterId;

    private String requesterName;

    private Integer status;

    private Long approverId;

    private String approverName;

    private String reason;

    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime handleTime;
}
