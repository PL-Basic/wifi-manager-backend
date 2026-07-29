package com.plagod.entity.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_client_signal")
public class ClientSignalRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long nodeId;
    private String deviceCode;
    private String mac;
    private Long sessionId;
    private Integer rssi;
    private String state;

    @TableField("report_time")
    private LocalDateTime reportTime;
}
