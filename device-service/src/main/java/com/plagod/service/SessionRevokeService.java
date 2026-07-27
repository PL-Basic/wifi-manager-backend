package com.plagod.service;

import com.plagod.vo.device.SessionRecordVO;

public interface SessionRevokeService {
    // 用户只能退出属于自己的 Session。
    SessionRecordVO logout(Long sessionId, Long userId);

    // 管理员可以撤销任意 Session。
    SessionRecordVO adminRevoke(Long sessionId, Integer operatorRole);
}