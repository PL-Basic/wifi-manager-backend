package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_device_wifi_config")
public class DeviceWifiConfigRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "wifi_config_id", type = IdType.AUTO)
    private Long wifiConfigId;

    private Long nodeId;
    private String deviceCode;

    // 与 t_device_command.request_id 一一对应。
    private String requestId;

    // 同一节点内严格递增，供 ESP32 拒绝旧命令覆盖新候选槽。
    private Long configVersion;

    // 允许查询候选 SSID，但绝不持久化 WiFi 密码。
    private String ssid;
    private Boolean passwordConfigured;
    private Integer status;

    private LocalDateTime stagedTime;
    private LocalDateTime activatedTime;
    private String failureMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}