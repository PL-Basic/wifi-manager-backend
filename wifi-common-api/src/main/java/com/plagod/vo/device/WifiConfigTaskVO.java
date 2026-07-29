package com.plagod.vo.device;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WifiConfigTaskVO {

    private Long wifiConfigId;
    private Long nodeId;
    private String deviceCode;
    private String requestId;
    private Long configVersion;
    private String ssid;

    // 只表示候选网络是否使用密码，绝不返回密码内容。
    private Boolean passwordConfigured;

    private Integer status;
    private String statusName;
    private LocalDateTime stagedTime;
    private LocalDateTime activatedTime;
    private String failureMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}