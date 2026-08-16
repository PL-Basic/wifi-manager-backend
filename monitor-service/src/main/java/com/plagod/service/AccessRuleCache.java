package com.plagod.service;

import com.plagod.entity.monitor.AccessRule;
import com.plagod.mapper.AccessRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AccessRuleCache {

    @Autowired
    private AccessRuleMapper accessRuleMapper;

    private volatile Map<Long, List<AccessRule>> enabledRulesByTenant =
            Collections.emptyMap();

    @PostConstruct
    public void reload() {
        QueryWrapper<AccessRule> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1);
        List<AccessRule> rules = accessRuleMapper.selectList(wrapper);
        if (rules == null || rules.isEmpty()) {
            this.enabledRulesByTenant = Collections.emptyMap();
            return;
        }

        Map<Long, List<AccessRule>> grouped = new LinkedHashMap<>();
        for (AccessRule rule : rules) {
            if (rule == null || rule.getTenantId() == null
                    || rule.getTenantId() <= 0) {
                throw new IllegalStateException(
                        "启用规则缺少有效租户归属");
            }
            grouped.computeIfAbsent(
                    rule.getTenantId(),
                    ignored -> new ArrayList<>()).add(rule);
        }

        Map<Long, List<AccessRule>> snapshot = new LinkedHashMap<>();
        grouped.forEach((tenantId, tenantRules) ->
                snapshot.put(
                        tenantId,
                        Collections.unmodifiableList(
                                new ArrayList<>(tenantRules))));
        this.enabledRulesByTenant = Collections.unmodifiableMap(snapshot);
    }

    public List<AccessRule> getEnabledRules(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("缺少有效租户身份");
        }
        return enabledRulesByTenant.getOrDefault(
                tenantId,
                Collections.emptyList());
    }
}
