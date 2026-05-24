package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.dto.AccessRuleCreateDTO;
import com.plagod.dto.AccessRulePageResult;
import com.plagod.dto.AccessRuleUpdateDTO;
import com.plagod.dto.AccessRuleVO;
import com.plagod.entity.AccessRule;
import com.plagod.mapper.AccessRuleMapper;
import com.plagod.service.AccessRuleCache;
import com.plagod.service.AccessRuleService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccessRuleServiceImpl implements AccessRuleService {

    @Autowired
    private AccessRuleMapper accessRuleMapper;

    @Autowired
    private AccessRuleCache accessRuleCache;

    @Override
    public AccessRuleVO create(AccessRuleCreateDTO createDTO) {
        QueryWrapper<AccessRule> existsWrapper = new QueryWrapper<>();
        existsWrapper.eq("rule_code", createDTO.getRuleCode());
        if (accessRuleMapper.selectCount(existsWrapper) > 0) {
            throw new IllegalArgumentException("规则编码已存在");
        }

        AccessRule entity = new AccessRule();
        BeanUtils.copyProperties(createDTO, entity);
        if (entity.getLevel() == null) {
            entity.setLevel(2);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        accessRuleMapper.insert(entity);
        accessRuleCache.reload();
        return toVO(accessRuleMapper.selectById(entity.getId()));
    }

    @Override
    public AccessRuleVO update(Long id, AccessRuleUpdateDTO updateDTO) {
        AccessRule entity = accessRuleMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("规则不存在");
        }
        if (updateDTO.getRuleType() != null) entity.setRuleType(updateDTO.getRuleType());
        if (updateDTO.getPattern() != null) entity.setPattern(updateDTO.getPattern());
        if (updateDTO.getActionType() != null) entity.setActionType(updateDTO.getActionType());
        if (updateDTO.getLevel() != null) entity.setLevel(updateDTO.getLevel());
        if (updateDTO.getEnabled() != null) entity.setEnabled(updateDTO.getEnabled());
        if (updateDTO.getDescription() != null) entity.setDescription(updateDTO.getDescription());
        accessRuleMapper.updateById(entity);
        accessRuleCache.reload();
        return toVO(accessRuleMapper.selectById(id));
    }

    @Override
    public AccessRuleVO get(Long id) {
        AccessRule entity = accessRuleMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("规则不存在");
        }
        return toVO(entity);
    }

    @Override
    public void delete(Long id) {
        int affected = accessRuleMapper.deleteById(id);
        if (affected == 0) {
            throw new IllegalArgumentException("规则不存在");
        }
        accessRuleCache.reload();
    }

    @Override
    public void toggleEnabled(Long id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new IllegalArgumentException("enabled 只能是 0 或 1");
        }
        AccessRule entity = accessRuleMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("规则不存在");
        }
        entity.setEnabled(enabled);
        accessRuleMapper.updateById(entity);
        accessRuleCache.reload();
    }

    @Override
    public AccessRulePageResult pageRules(long current, long size, Integer ruleType, Integer enabled, String keyword) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<AccessRule> queryWrapper = new QueryWrapper<>();
        if (ruleType != null) {
            queryWrapper.eq("rule_type", ruleType);
        }
        if (enabled != null) {
            queryWrapper.eq("enabled", enabled);
        }
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like("rule_code", keyword)
                    .or().like("pattern", keyword)
                    .or().like("description", keyword));
        }
        queryWrapper.orderByDesc("create_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AccessRule> page =
                accessRuleMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageCurrent, pageSize), queryWrapper);

        List<AccessRuleVO> records = new ArrayList<>();
        for (AccessRule item : page.getRecords()) {
            records.add(toVO(item));
        }

        AccessRulePageResult result = new AccessRulePageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    private AccessRuleVO toVO(AccessRule entity) {
        AccessRuleVO vo = new AccessRuleVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
