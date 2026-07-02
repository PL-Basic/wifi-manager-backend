package com.plagod.service;

import com.plagod.dto.monitor.AccessRuleCreateDTO;
import com.plagod.vo.monitor.AccessRulePageResult;
import com.plagod.dto.monitor.AccessRuleUpdateDTO;
import com.plagod.vo.monitor.AccessRuleVO;

public interface AccessRuleService {

    AccessRuleVO create(AccessRuleCreateDTO createDTO);

    AccessRuleVO update(Long id, AccessRuleUpdateDTO updateDTO);

    AccessRuleVO get(Long id);

    void delete(Long id);

    void toggleEnabled(Long id, Integer enabled);

    AccessRulePageResult pageRules(long current, long size, Integer ruleType, Integer enabled, String keyword);
}
