package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_support_ticket_message")
public class SupportTicketMessage {

    @TableId(type = IdType.AUTO)
    private Long messageId;
    private Long ticketId;
    private Long tenantId;
    private String senderType;
    private Long senderUserId;
    @TableField(
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private String senderKey;
    private String clientRequestId;
    private String contentText;
    private Integer userVisible;
    private LocalDateTime createTime;
}
