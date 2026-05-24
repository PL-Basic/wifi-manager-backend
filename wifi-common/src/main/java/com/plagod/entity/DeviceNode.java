package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("wifi_device")
public class DeviceNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "device_id", type = IdType.AUTO)
    private Long deviceId;

    private String deviceCode;

    private String deviceName;

    private String ipAddress;

    private String macAddress;

    private Integer status;

    private String firmwareVersion;

    private LocalDateTime lastHeartbeatTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField("del_flag")
    @TableLogic
    private Integer delFlag;
}
