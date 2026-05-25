package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_operation_request")
public class UserOperationRequest {

    @TableId(value = "id", type = IdType.AUTO)
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
