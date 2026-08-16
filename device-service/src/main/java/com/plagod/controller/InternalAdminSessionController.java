package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.service.SessionQueryService;
import com.plagod.service.SessionRevokeService;
import com.plagod.vo.device.SessionPageResult;
import com.plagod.vo.device.SessionRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/sessions")
public class InternalAdminSessionController {

    @Autowired
    private SessionQueryService sessionQueryService;
    @Autowired
    private SessionRevokeService sessionRevokeService;

    @GetMapping
    public ApiResponse<SessionPageResult> pageSessions(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String mac,
            @RequestParam(required = false) Long nodeId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(
                sessionQueryService.pageSessions(
                        tenantId, current, size, mac, nodeId, userId, status));
    }

    @PostMapping("/{sessionId}/revoke")
    public ApiResponse<SessionRecordVO> revoke(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-User-Role", required = false) Integer operatorRole) {
        return ApiResponse.success(
                sessionRevokeService.adminRevoke(tenantId, sessionId, operatorRole));
    }
}
