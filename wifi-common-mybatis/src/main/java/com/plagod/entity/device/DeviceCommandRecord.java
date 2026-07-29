package com.plagod.entity.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_device_command")
public class DeviceCommandRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "command_id", type = IdType.AUTO)
    private Long commandId;

    // 后端生成、固件原样返回的命令唯一编号。
    private String requestId;

    private Long nodeId;
    private String deviceCode;

    // 固件命令类型，例如 ALLOW、REVOKE_ACCESS。
    private String commandType;

    // 后端业务目的，例如 PORTAL_AUTHORIZE、LEASE_RENEW。
    private String purpose;

    private Long sessionId;
    private String mac;
    private Long alertId;
    private Integer ttlSeconds;

    private String topic;
    private String payload;
    private String encryptedPayload;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;

    private LocalDateTime publishTime;
    private LocalDateTime deadlineTime;
    private LocalDateTime resultTime;
    private String resultMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
