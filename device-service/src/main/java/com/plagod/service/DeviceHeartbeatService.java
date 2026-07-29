package com.plagod.service;

import java.time.LocalDateTime;

public interface DeviceHeartbeatService {

    // 将指定截止时间之前仍未更新心跳的在线节点标记为离线。
    int markTimedOutNodesOffline(LocalDateTime cutoff);
}