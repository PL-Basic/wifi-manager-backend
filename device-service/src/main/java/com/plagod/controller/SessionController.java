package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.service.PortalSessionService;
import com.plagod.service.SessionRevokeService;
import com.plagod.vo.device.SessionPageResult;
import com.plagod.service.SessionQueryService;
import com.plagod.vo.device.SessionRecordVO;
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
    private SessionRevokeService sessionRevokeService;

    // X-User-Id 类似于快递单号
    @PostMapping("/portal-authorize")
    public ApiResponse<SessionRecordVO> portalAuthorize(@Valid @RequestBody PortalAuthorizeDTO portalAuthorizeDTO,
                                                        @RequestHeader("X-User-Id") Long userId) {
        SessionRecordVO session = portalSessionService.authorize(portalAuthorizeDTO, userId);
        return ApiResponse.success("Portal 认证请求已受理", session);
    }

    @GetMapping
    public ApiResponse<SessionPageResult> pageSessions(@RequestParam(defaultValue = "1") Long current,
                                                       @RequestParam(defaultValue = "10") Long size,
                                                       @RequestParam(required = false) String mac,
                                                       @RequestParam(required = false) Long nodeId,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) Integer status) {
        return ApiResponse.success(sessionQueryService.pageSessions(current, size, mac, nodeId, userId, status));
    }

    @PostMapping("/{sessionId}/logout")
    public ApiResponse<SessionRecordVO> logout(@PathVariable Long sessionId,
                                               @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success("Session 退出请求已受理", sessionRevokeService.logout(sessionId, userId));
    }

    @PostMapping("/{sessionId}/admin-revoke")
    public ApiResponse<SessionRecordVO> adminRevoke(@PathVariable Long sessionId,
                                                    @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {
        return ApiResponse.success("Session 撤销请求已受理", sessionRevokeService.adminRevoke(sessionId, operatorRole));
    }
}