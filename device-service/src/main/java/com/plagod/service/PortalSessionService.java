package com.plagod.service;

import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.vo.device.SessionRecordVO;

public interface PortalSessionService {
    SessionRecordVO authorize(PortalAuthorizeDTO dto, Long userId);
}
