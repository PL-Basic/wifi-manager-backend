package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.service.*;
import com.plagod.vo.device.SessionPageResult;
import com.plagod.vo.device.SessionRecordVO;
import com.plagod.vo.portal.PortalSessionStatusVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public ApiResponse<PortalSessionStatusVO> portalAuthorize(@Valid @RequestBody PortalAuthorizeDTO dto,
                                                              @RequestHeader("X-User-Id") Long userId) {

        SessionRecordVO session = portalSessionService.authorize(dto, userId);
        PortalSessionStatusVO status = portalSessionStatusQueryService.getOwnedStatus(session.getSessionId(), userId);

        return ApiResponse.success("Portal 认证请求已受理", status);
    }

    @GetMapping("/{sessionId}/portal-status")
    public ApiResponse<PortalSessionStatusVO> getPortalStatus(@PathVariable Long sessionId,
                                                              @RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success(portalSessionStatusQueryService.getOwnedStatus(sessionId, userId));
    }

    @GetMapping
    public ApiResponse<SessionPageResult> pageOwnedSessions(@RequestParam(defaultValue = "1") Long current,
                                                            @RequestParam(defaultValue = "10") Long size,
                                                            @RequestParam(required = false) String mac,
                                                            @RequestParam(required = false) Long nodeId,
                                                            @RequestParam(required = false) Integer status,
                                                            @RequestHeader("X-User-Id") Long userId) {

        // 查询归属强制使用 Gateway 身份，不接受请求参数指定其他用户。
        return ApiResponse.success(sessionQueryService.pageSessions(
                current, size, mac, nodeId, userId, status));
    }

    @PostMapping("/{sessionId}/logout")
    public ApiResponse<SessionRecordVO> logout(@PathVariable Long sessionId,
                                               @RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success("Session 退出请求已受理", sessionRevokeService.logout(sessionId, userId));
    }
}