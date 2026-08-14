package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_support_ticket_transition")
public class SupportTicketTransition {

    @TableId(type = IdType.AUTO)
    private Long transitionId;
    private Long ticketId;
    private Long tenantId;
    private String eventKey;
    private String fromStatus;
    private String toStatus;
    private String operatorType;
    private Long operatorUserId;
    private String reasonCode;
    private LocalDateTime createTime;
}
