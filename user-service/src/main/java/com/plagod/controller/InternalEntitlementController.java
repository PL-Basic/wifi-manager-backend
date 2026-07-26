package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.entity.EntitlementUsageLog;
import com.plagod.mapper.DurationPurchaseMapper;
import com.plagod.mapper.EntitlementUsageLogMapper;
import com.plagod.mapper.NetworkEntitlementMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.EntitlementLeaseService;
import com.plagod.vo.user.EntitlementLeaseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/internal/entitlements")
public class InternalEntitlementController {

    @Autowired
    private EntitlementLeaseService entitlementLeaseService;

    @PostMapping("/lease")
    public ApiResponse<EntitlementLeaseResult> acquireLease(@Valid @RequestBody EntitlementLeaseRequest request) {
        return ApiResponse.success(entitlementLeaseService.acquireLease(request));
    }
}