package com.plagod.client;

import com.plagod.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "monitor-service")
public interface MonitorServiceClient {

    @GetMapping("/rules")
    ApiResponse<Object> pageRules(@RequestParam("current") Long current,
                                  @RequestParam("size") Long size,
                                  @RequestParam(value = "ruleType", required = false) Integer ruleType,
                                  @RequestParam(value = "enabled", required = false) Integer enabled,
                                  @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/rules/{id}")
    ApiResponse<Object> getRule(@PathVariable("id") Long id);

    @PostMapping("/rules")
    ApiResponse<Object> createRule(@RequestBody Map<String, Object> body);

    @PutMapping("/rules/{id}")
    ApiResponse<Object> updateRule(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @DeleteMapping("/rules/{id}")
    ApiResponse<Void> deleteRule(@PathVariable("id") Long id);

    @PatchMapping("/rules/{id}/enabled")
    ApiResponse<Void> toggleRule(@PathVariable("id") Long id, @RequestParam("enabled") Integer enabled);

    @GetMapping("/alerts")
    ApiResponse<Object> pageAlerts(@RequestParam("current") Long current,
                                   @RequestParam("size") Long size,
                                   @RequestParam(value = "level", required = false) Integer level,
                                   @RequestParam(value = "status", required = false) Integer status,
                                   @RequestParam(value = "mac", required = false) String mac,
                                   @RequestParam(value = "startTime", required = false) String startTime,
                                   @RequestParam(value = "endTime", required = false) String endTime);

    @PatchMapping("/alerts/{id}/handle")
    ApiResponse<Void> handleAlert(@PathVariable("id") Long id, @RequestParam("handleUserId") Long handleUserId);

    @GetMapping("/alerts/{id}")
    ApiResponse<Object> getAlert(@PathVariable("id") Long id);

    @GetMapping("/audits")
    ApiResponse<Object> pageAudits(@RequestParam("current") Long current,
                                   @RequestParam("size") Long size,
                                   @RequestParam(value = "action", required = false) String action,
                                   @RequestParam(value = "operatorName", required = false) String operatorName,
                                   @RequestParam(value = "target", required = false) String target,
                                   @RequestParam(value = "startTime", required = false) String startTime,
                                   @RequestParam(value = "endTime", required = false) String endTime);

    @GetMapping("/audits/{id}")
    ApiResponse<Object> getAudit(@PathVariable("id") Long id);

    @GetMapping("/locations")
    ApiResponse<Object> pageLocations(@RequestParam("current") Long current,
                                      @RequestParam("size") Long size,
                                      @RequestParam(value = "mac", required = false) String mac,
                                      @RequestParam(value = "userId", required = false) Long userId,
                                      @RequestParam(value = "startTime", required = false) String startTime,
                                      @RequestParam(value = "endTime", required = false) String endTime);
}
