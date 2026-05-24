package com.plagod.service;

import com.plagod.entity.AccessRule;
import com.plagod.mapper.AccessRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Component
public class AccessRuleCache {

    @Autowired
    private AccessRuleMapper accessRuleMapper;

    private volatile List<AccessRule> enabledRules = Collections.emptyList();

    @PostConstruct
    public void reload() {
        QueryWrapper<AccessRule> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1);
        List<AccessRule> rules = accessRuleMapper.selectList(wrapper);
        this.enabledRules = rules == null ? Collections.emptyList() : rules;
    }

    public List<AccessRule> getEnabledRules() {
        return enabledRules;
    }
}
