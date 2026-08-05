package com.plagod.service;

import com.plagod.dto.auth.AuthResultDTO;
import com.plagod.vo.AuthSessionIssue;
import com.plagod.vo.auth.SessionValidationVO;
import com.plagod.vo.tenant.TenantContextVO;

public interface AuthSessionService {

    AuthSessionIssue open(AuthResultDTO identity,
                          String clientInstanceId,
                          String userAgent,
                          String clientIp);

    AuthSessionIssue refresh(String refreshToken,
                             String clientInstanceId,
                             String userAgent,
                             String clientIp);

    AuthSessionIssue refreshAfterStepUp(String refreshToken,
                                        String target,
                                        String code,
                                        String clientInstanceId,
                                        String userAgent,
                                        String clientIp);

    AuthSessionIssue replace(String currentRefreshToken,
                             String expectedSessionId,
                             AuthResultDTO nextIdentity,
                             String clientInstanceId,
                             String userAgent,
                             String clientIp);

    AuthResultDTO switchContext(String sessionId,
                                Long userId,
                                Integer role,
                                TenantContextVO context);

    void logout(String refreshToken, String reason);

    void revokeSession(String sessionId, String reason);

    void revokeAllForUser(Long userId, String reason);

    SessionValidationVO validate(String sessionId, Long userId, String tokenId);

    void revokeAccessToken(String tokenId, long expiresAtEpochMillis, String reason);
}
