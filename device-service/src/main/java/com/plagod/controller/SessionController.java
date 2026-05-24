package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.SessionPageResult;
import com.plagod.service.SessionQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    @Autowired
    private SessionQueryService sessionQueryService;

    @GetMapping
    public ApiResponse<SessionPageResult> pageSessions(@RequestParam(defaultValue = "1") Long current,
                                                       @RequestParam(defaultValue = "10") Long size,
                                                       @RequestParam(required = false) String mac,
                                                       @RequestParam(required = false) Long nodeId,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) Integer status) {
        return ApiResponse.success(sessionQueryService.pageSessions(current, size, mac, nodeId, userId, status));
    }
}
