package com.plagod.service;


import com.plagod.vo.device.ClientSignalPageResult;

import java.time.LocalDateTime;

public interface ClientSignalQueryService {

    ClientSignalPageResult pageClientSignals(long current, long size, String deviceCode, Long nodeId, String mac, Long sessionId, String state, LocalDateTime startTime, LocalDateTime endTime);

    // 判断指定客户端是否在时间窗口内被当前 ESP32 节点观察到。
    boolean wasRecentlyObserved(Long nodeId, String deviceCode, String mac, LocalDateTime sinceTime);
}
