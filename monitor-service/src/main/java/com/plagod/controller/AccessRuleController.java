package com.plagod.controller;

import com.plagod.dto.AccessRuleCreateDTO;
import com.plagod.dto.AccessRulePageResult;
import com.plagod.dto.AccessRuleUpdateDTO;
import com.plagod.dto.AccessRuleVO;
import com.plagod.dto.ApiResponse;
import com.plagod.service.AccessRuleService;
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
@RequestMapping("/rules")
public class AccessRuleController {

    @Autowired
    private AccessRuleService accessRuleService;

    @GetMapping
    public ApiResponse<AccessRulePageResult> pageRules(@RequestParam(defaultValue = "1") Long current,
                                                       @RequestParam(defaultValue = "10") Long size,
                                                       @RequestParam(required = false) Integer ruleType,
                                                       @RequestParam(required = false) Integer enabled,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.success(accessRuleService.pageRules(current, size, ruleType, enabled, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<AccessRuleVO> get(@PathVariable Long id) {
        return ApiResponse.success(accessRuleService.get(id));
    }

    @PostMapping
    public ApiResponse<AccessRuleVO> create(@Valid @RequestBody AccessRuleCreateDTO createDTO) {
        return ApiResponse.success("规则创建成功", accessRuleService.create(createDTO));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccessRuleVO> update(@PathVariable Long id, @Valid @RequestBody AccessRuleUpdateDTO updateDTO) {
        return ApiResponse.success("规则更新成功", accessRuleService.update(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        accessRuleService.delete(id);
        return ApiResponse.success("规则已删除", null);
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<Void> toggleEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        accessRuleService.toggleEnabled(id, enabled);
        return ApiResponse.success("规则启停已更新", null);
    }
}
