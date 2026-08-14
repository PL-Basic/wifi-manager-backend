package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.device.MacBlacklistCreateDTO;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.MacBlacklistService;
import com.plagod.vo.device.MacBlacklistPageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/internal/admin/blacklist")
public class MacBlacklistController {

    @Autowired
    private DeviceCommandService deviceCommandService;
    @Autowired
    private MacBlacklistService macBlacklistService;

    @GetMapping
    public ApiResponse<MacBlacklistPageResult> pageBlacklist(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(
                deviceCommandService.pageBlacklist(tenantId, current, size, keyword));
    }

    @PostMapping
    public ApiResponse<Void> addBlacklist(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Valid @RequestBody MacBlacklistCreateDTO request) {
        macBlacklistService.addBlacklist(tenantId, request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{mac}")
    public ApiResponse<Void> removeBlacklist(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable String mac) {
        deviceCommandService.removeBlacklist(tenantId, mac);
        return ApiResponse.success(null);
    }
}
