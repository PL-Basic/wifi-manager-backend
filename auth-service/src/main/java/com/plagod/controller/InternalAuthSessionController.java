package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.AuthSessionService;
import com.plagod.vo.auth.SessionValidationVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth/sessions")
public class InternalAuthSessionController {

    private final AuthSessionService authSessionService;

    public InternalAuthSessionController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @GetMapping("/{sessionId}/validate")
    public ApiResponse<SessionValidationVO> validate(
            @PathVariable String sessionId,
            @RequestParam Long userId,
            @RequestParam String jti) {
        return ApiResponse.success(authSessionService.validate(sessionId, userId, jti));
    }

    @PostMapping("/{sessionId}/revoke")
    public ApiResponse<Void> revoke(@PathVariable String sessionId,
                                    @RequestParam(defaultValue = "SECURITY_EVENT") String reason) {
        authSessionService.revokeSession(sessionId, reason);
        return ApiResponse.success(null);
    }

    @PostMapping("/users/{userId}/revoke")
    public ApiResponse<Void> revokeAll(@PathVariable Long userId,
                                       @RequestParam(defaultValue = "SECURITY_EVENT") String reason) {
        authSessionService.revokeAllForUser(userId, reason);
        return ApiResponse.success(null);
    }

    @PostMapping("/access-tokens/{jti}/revoke")
    public ApiResponse<Void> revokeAccessToken(
            @PathVariable String jti,
            @RequestParam long expiresAtEpochMillis,
            @RequestParam(defaultValue = "SECURITY_EVENT") String reason) {
        authSessionService.revokeAccessToken(jti, expiresAtEpochMillis, reason);
        return ApiResponse.success(null);
    }
}
