package com.plagod.service;

import com.plagod.entity.device.SessionRecord;

import java.time.LocalDateTime;

public interface SessionLeaseService {
    // 处理活跃 Session 的续租、扣费或离线结算。
    void processSession(Long sessionId);

    // 用户退出或管理员撤销前，结算最后一次已观测到的在线使用时长。
    // 调用方必须已经锁住对应 Session 行，并处于数据库事务中。
    void settleFinalUsage(SessionRecord session, LocalDateTime now);
}