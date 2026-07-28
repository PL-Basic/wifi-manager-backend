package com.plagod.client;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.monitor.AccessRuleCreateDTO;
import com.plagod.dto.monitor.AccessRuleUpdateDTO;
import com.plagod.vo.monitor.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@FeignClient(name = "monitor-service")
public interface MonitorServiceClient {

    @GetMapping("/internal/admin/rules")
    ApiResponse<AccessRulePageResult> pageRules(@RequestParam("current") Long current,
                                                @RequestParam("size") Long size,
                                                @RequestParam(value = "ruleType", required = false) Integer ruleType,
                                                @RequestParam(value = "enabled", required = false) Integer enabled,
                                                @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/internal/admin/rules/{id}")
    ApiResponse<AccessRuleVO> getRule(@PathVariable("id") Long id);

    @PostMapping("/internal/admin/rules")
    ApiResponse<AccessRuleVO> createRule(@Valid @RequestBody AccessRuleCreateDTO createDTO);

    @PutMapping("/internal/admin/rules/{id}")
    ApiResponse<AccessRuleVO> updateRule(@PathVariable("id") Long id,@Valid @RequestBody AccessRuleUpdateDTO updateDTO);

    @DeleteMapping("/internal/admin/rules/{id}")
    ApiResponse<Void> deleteRule(@PathVariable("id") Long id);

    @PatchMapping("/internal/admin/rules/{id}/enabled")
    ApiResponse<Void> toggleRule(@PathVariable("id") Long id, @RequestParam("enabled") Integer enabled);

    @GetMapping("/internal/admin/alerts")
    ApiResponse<AlertEventPageResult> pageAlerts(@RequestParam("current") Long current,
                                                 @RequestParam("size") Long size,
                                                 @RequestParam(value = "level", required = false) Integer level,
                                                 @RequestParam(value = "status", required = false) Integer status,
                                                 @RequestParam(value = "mac", required = false) String mac,
                                                 @RequestParam(value = "startTime", required = false) String startTime,
                                                 @RequestParam(value = "endTime", required = false) String endTime);

    @GetMapping("/internal/admin/alerts/{id}")
    ApiResponse<AlertEventVO> getAlert(@PathVariable("id") Long id);

    @PatchMapping("/internal/admin/alerts/{id}/handle")
    ApiResponse<Void> handleAlert(@PathVariable("id") Long id, @RequestHeader("X-User-Id") Long handleUserId);

    @GetMapping("/internal/admin/audits")
    ApiResponse<AuditLogPageResult> pageAudits(@RequestParam("current") Long current,
                                               @RequestParam("size") Long size,
                                               @RequestParam(value = "action", required = false) String action,
                                               @RequestParam(value = "operatorName", required = false) String operatorName,
                                               @RequestParam(value = "target", required = false) String target,
                                               @RequestParam(value = "startTime", required = false) String startTime,
                                               @RequestParam(value = "endTime", required = false) String endTime);

    @GetMapping("/internal/admin/audits/{id}")
    ApiResponse<AuditLogVO> getAudit(@PathVariable("id") Long id);

    @GetMapping("/internal/admin/locations")
    ApiResponse<ClientLocationPageResult> pageLocations(@RequestParam("current") Long current,
                                                        @RequestParam("size") Long size,
                                                        @RequestParam(value = "mac", required = false) String mac,
                                                        @RequestParam(value = "userId", required = false) Long userId,
                                                        @RequestParam(value = "startTime", required = false) String startTime,
                                                        @RequestParam(value = "endTime", required = false) String endTime);
}
