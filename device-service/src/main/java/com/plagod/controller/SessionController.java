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

    /**
     * 创建或复用 Portal Session，再返回包含真实命令状态的专用结果。
     */
    @PostMapping("/portal-authorize")
    public ApiResponse<PortalSessionStatusVO> portalAuthorize(@Valid @RequestBody PortalAuthorizeDTO portalAuthorizeDTO,
                                                              @RequestHeader("X-User-Id") Long userId) {

        SessionRecordVO session = portalSessionService.authorize(portalAuthorizeDTO, userId);

        PortalSessionStatusVO status = portalSessionStatusQueryService.getOwnedStatus(session.getSessionId(), userId);

        return ApiResponse.success("Portal 认证请求已受理", status);
    }

    /**
     * Portal 使用本人身份轮询单个 Session 的授权执行状态。
     */
    @GetMapping("/{sessionId}/portal-status")
    public ApiResponse<PortalSessionStatusVO> getPortalStatus(@PathVariable("sessionId") Long sessionId,
                                                              @RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success(portalSessionStatusQueryService.getOwnedStatus(sessionId, userId));
    }

    @GetMapping
    public ApiResponse<SessionPageResult> pageSessions(@RequestParam(defaultValue = "1") Long current,
                                                       @RequestParam(defaultValue = "10") Long size,
                                                       @RequestParam(required = false) String mac,
                                                       @RequestParam(required = false) Long nodeId,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) Integer status) {

        return ApiResponse.success(
                sessionQueryService.pageSessions(current, size, mac, nodeId, userId, status));
    }

    @PostMapping("/{sessionId}/logout")
    public ApiResponse<SessionRecordVO> logout(@PathVariable("sessionId") Long sessionId,
                                               @RequestHeader("X-User-Id") Long userId) {

        return ApiResponse.success("Session 退出请求已受理", sessionRevokeService.logout(sessionId, userId));
    }

    @PostMapping("/{sessionId}/admin-revoke")
    public ApiResponse<SessionRecordVO> adminRevoke(@PathVariable("sessionId") Long sessionId,
                                                    @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {

        return ApiResponse.success("Session 撤销请求已受理", sessionRevokeService.adminRevoke(sessionId, operatorRole));
    }
}