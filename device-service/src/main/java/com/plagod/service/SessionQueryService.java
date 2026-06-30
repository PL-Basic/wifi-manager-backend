package com.plagod.service;

import com.plagod.vo.SessionPageResult;

public interface SessionQueryService {
    SessionPageResult pageSessions(long current, long size, String mac, Long nodeId, Long userId, Integer status);
}
