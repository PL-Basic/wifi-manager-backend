package com.plagod.service;

import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.vo.device.SessionRecordVO;

public interface PortalSessionService {
    SessionRecordVO authorize(PortalAuthorizeDTO dto, Long userId);

    // FORCE_LOGIN_REPLACE 成功后继续新 Session 的授权。
    // 调用方必须已经位于命令结果事务中。
    void activateWaitingReplacement(Long replacedSessionId);
}
