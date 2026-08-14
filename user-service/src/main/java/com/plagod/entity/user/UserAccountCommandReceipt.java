package com.plagod.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_account_command_receipt")
public class UserAccountCommandReceipt {

    @TableId(type = IdType.AUTO)
    private Long receiptId;
    private String commandType;
    private String idempotencyKey;
    private String requestFingerprint;
    private Long userId;
    private String resultStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
