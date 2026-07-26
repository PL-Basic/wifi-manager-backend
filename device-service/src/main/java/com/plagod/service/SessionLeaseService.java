package com.plagod.service;

public interface SessionLeaseService {
    // 处理一个活跃 Session 的续租、扣费或离线结算。
    void processSession(Long sessionId);
}
