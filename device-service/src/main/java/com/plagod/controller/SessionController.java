package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.service.PortalSessionService;
import com.plagod.service.PortalSessionStatusQueryService;
import com.plagod.service.SessionQueryService;
import com.plagod.service.SessionRevokeService;
import com.plagod.vo.device.SessionPageResult;
import com.plagod.vo.device.SessionRecordVO;
import com.plagod.vo.portal.PortalSessionStatusVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    @Autowired
    private SessionQueryService sessionQueryService;
    @Autowired
    private PortalSessionService portalSessionService;
    @Autowired
    private PortalSessionStatusQueryService portalSessionStatusQueryService;
    @Autowired
    private SessionRevokeService sessionRevokeService;

    @PostMapping("/portal-authorize")
    public ApiResponse<PortalSessionStatusVO> portalAuthorize(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Valid @RequestBody PortalAuthorizeDTO request,
            @RequestHeader("X-User-Id") Long userId) {
        SessionRecordVO session = portalSessionService.authorize(tenantId, request, userId);
        return ApiResponse.success(
                portalSessionStatusQueryService.getOwnedStatus(
                        tenantId, session.getSessionId(), userId));
    }

    @GetMapping("/{sessionId}/portal-status")
    public ApiResponse<PortalSessionStatusVO> getPortalStatus(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long sessionId,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(
                portalSessionStatusQueryService.getOwnedStatus(
                        tenantId, sessionId, userId));
    }

    @GetMapping
    public ApiResponse<SessionPageResult> pageOwnedSessions(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String mac,
            @RequestParam(required = false) Long nodeId,
            @RequestParam(required = false) Integer status,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(
                sessionQueryService.pageSessions(
                        tenantId, current, size, mac, nodeId, userId, status));
    }

    @PostMapping("/{sessionId}/logout")
    public ApiResponse<SessionRecordVO> logout(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long sessionId,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(
                sessionRevokeService.logout(tenantId, sessionId, userId));
    }
}
