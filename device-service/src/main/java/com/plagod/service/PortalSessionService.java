package com.plagod.service;

import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.vo.device.SessionRecordVO;

public interface PortalSessionService {
    SessionRecordVO authorize(Long tenantId, PortalAuthorizeDTO dto, Long userId);

    // FORCE_LOGIN_REPLACE 的命令结果提交后，继续新 Session 的授权。
    void activateWaitingReplacement(Long tenantId, Long replacedSessionId);
}
