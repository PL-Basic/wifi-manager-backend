package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.SessionQueryService;
import com.plagod.service.SessionRevokeService;
import com.plagod.vo.device.SessionPageResult;
import com.plagod.vo.device.SessionRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/admin/sessions")
public class InternalAdminSessionController {

    @Autowired
    private SessionQueryService sessionQueryService;

    @Autowired
    private SessionRevokeService sessionRevokeService;

    @GetMapping
    public ApiResponse<SessionPageResult> pageSessions(@RequestParam(defaultValue = "1") Long current,
                                                       @RequestParam(defaultValue = "10") Long size,
                                                       @RequestParam(required = false) String mac,
                                                       @RequestParam(required = false) Long nodeId,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) Integer status) {

        return ApiResponse.success(sessionQueryService.pageSessions(current, size, mac, nodeId, userId, status));
    }

    @PostMapping("/{sessionId}/revoke")
    public ApiResponse<SessionRecordVO> revoke(@PathVariable Long sessionId,
                                               @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                               @RequestHeader(value = "X-User-Name", required = false) String operatorName,
                                               @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {

        // operatorId/operatorName 继续留在请求 Header 中供审计切面读取。
        return ApiResponse.success("Session 撤销请求已受理", sessionRevokeService.adminRevoke(sessionId, operatorRole));
    }
}