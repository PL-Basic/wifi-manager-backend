package com.plagod.controller;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.monitor.AccessRuleCreateDTO;
import com.plagod.dto.monitor.AccessRuleUpdateDTO;
import com.plagod.vo.monitor.AccessRulePageResult;
import com.plagod.vo.monitor.AccessRuleVO;
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

import javax.validation.Valid;

@RestController
@RequestMapping("/admin/rules")
public class AdminRuleController {

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @GetMapping
    public ApiResponse<AccessRulePageResult> pageRules(@RequestParam(defaultValue = "1") Long current,
                                                       @RequestParam(defaultValue = "10") Long size,
                                                       @RequestParam(required = false) Integer ruleType,
                                                       @RequestParam(required = false) Integer enabled,
                                                       @RequestParam(required = false) String keyword) {
        return monitorServiceClient.pageRules(current, size, ruleType, enabled, keyword);
    }

    @GetMapping("/{id}")
    public ApiResponse<AccessRuleVO> getRule(@PathVariable Long id) {
        return monitorServiceClient.getRule(id);
    }

    @PostMapping
    public ApiResponse<AccessRuleVO> createRule(@Valid @RequestBody AccessRuleCreateDTO accessRuleCreateDTO) {
        return monitorServiceClient.createRule(accessRuleCreateDTO);
    }

    @PutMapping("/{id}")
    public ApiResponse<AccessRuleVO> updateRule(@PathVariable Long id, @Valid @RequestBody AccessRuleUpdateDTO accessRuleUpdateDTO) {
        return monitorServiceClient.updateRule(id, accessRuleUpdateDTO);
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
