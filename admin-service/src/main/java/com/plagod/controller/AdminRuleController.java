package com.plagod.controller;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/rules")
public class AdminRuleController {

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @GetMapping
    public ApiResponse<Object> pageRules(@RequestParam(defaultValue = "1") Long current,
                                         @RequestParam(defaultValue = "10") Long size,
                                         @RequestParam(required = false) Integer ruleType,
                                         @RequestParam(required = false) Integer enabled,
                                         @RequestParam(required = false) String keyword) {
        return monitorServiceClient.pageRules(current, size, ruleType, enabled, keyword);
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> getRule(@PathVariable Long id) {
        return monitorServiceClient.getRule(id);
    }

    @PostMapping
    public ApiResponse<Object> createRule(@RequestBody Map<String, Object> body) {
        return monitorServiceClient.createRule(body);
    }

    @PutMapping("/{id}")
    public ApiResponse<Object> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return monitorServiceClient.updateRule(id, body);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        return monitorServiceClient.deleteRule(id);
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<Void> toggleRule(@PathVariable Long id, @RequestParam Integer enabled) {
        return monitorServiceClient.toggleRule(id, enabled);
    }
}
