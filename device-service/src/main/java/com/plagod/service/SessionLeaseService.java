package com.plagod.service;

public interface SessionLeaseService {
    // 处理活跃 Session 的续租、扣费或离线结算。
    void processSession(Long sessionId);

    // Portal 编排在本地事务外触发的最终用量结算。
    void settleFinalUsage(Long sessionId);
}
