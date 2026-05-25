package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.AuditLogPageResult;
import com.plagod.dto.AuditLogVO;
import com.plagod.service.AuditLogQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/audits")
public class AuditLogController {

    @Autowired
    private AuditLogQueryService auditLogQueryService;

    @GetMapping
    public ApiResponse<AuditLogPageResult> pageAudits(@RequestParam(defaultValue = "1") Long current,
                                                      @RequestParam(defaultValue = "10") Long size,
                                                      @RequestParam(required = false) String action,
                                                      @RequestParam(required = false) String operatorName,
                                                      @RequestParam(required = false) String target,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(auditLogQueryService.pageAudits(current, size, action, operatorName, target, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuditLogVO> getAudit(@PathVariable Long id) {
        return ApiResponse.success(auditLogQueryService.getAudit(id));
    }
}
