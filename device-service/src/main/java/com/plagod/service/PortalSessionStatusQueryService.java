package com.plagod.service;

import com.plagod.vo.portal.PortalSessionStatusVO;

public interface PortalSessionStatusQueryService {

    PortalSessionStatusVO getOwnedStatus(Long sessionId, Long userId);
}