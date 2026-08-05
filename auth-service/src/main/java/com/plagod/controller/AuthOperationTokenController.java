package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.auth.OperationTokenConsumeRequest;
import com.plagod.dto.auth.OperationTokenIssueRequest;
import com.plagod.service.AuthOperationTokenService;
import com.plagod.utils.RequestIpUtils;
import com.plagod.vo.auth.OperationTokenConsumptionVO;
import com.plagod.vo.auth.OperationTokenVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping
public class AuthOperationTokenController {

    private final AuthOperationTokenService operationTokenService;

    public AuthOperationTokenController(AuthOperationTokenService operationTokenService) {
        this.operationTokenService = operationTokenService;
    }

    @PostMapping("/auth/operation-tokens")
    public ApiResponse<OperationTokenVO> issue(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Session-Id") String sessionId,
            @Valid @RequestBody OperationTokenIssueRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(operationTokenService.issue(
                userId,
                sessionId,
                request,
                RequestIpUtils.getClientIP(servletRequest)));
    }

    @PostMapping("/internal/auth/operation-tokens/consume")
    public ApiResponse<OperationTokenConsumptionVO> consume(
            @Valid @RequestBody OperationTokenConsumeRequest request) {
        return ApiResponse.success(operationTokenService.consume(request));
    }
}
