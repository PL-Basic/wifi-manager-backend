package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_support_ticket")
public class SupportTicket {

    @TableId(type = IdType.AUTO)
    private Long ticketId;
    private String ticketNo;
    private Long tenantId;
    private Long sourceSubmissionId;
    private Long requesterUserId;
    private String titleSnapshot;
    private String categoryCode;
    private String priorityCode;
    private String status;
    private Long assigneeUserId;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
