package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
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
import java.util.regex.Pattern;

@Service
public class AccessRuleServiceImpl implements AccessRuleService {

    // IPv4
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$"
    );

    // IPv4 CIDR
    private static final Pattern IPV4_CIDR = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)/(3[0-2]|[12]\\d|\\d)$"
    );

    // IPv6
    private static final Pattern IPV6 = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^(([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?::(([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?$"
    );

    // IPv6 CIDR
    private static final Pattern IPV6_CIDR = Pattern.compile(
            "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:)*:([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})/(12[0-8]|1[01]\\d|[1-9]\\d|\\d)$"
    );

    // 完整域名 或 通配符域名 或 关键词片段
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(\\*\\.)?([a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?$"
    );

    @Autowired
    private AccessRuleMapper accessRuleMapper;

    @Autowired
    private AccessRuleCache accessRuleCache;

    @Override
    @Audited(action = "rule.create")
    public AccessRuleVO create(AccessRuleCreateDTO createDTO) {
        createDTO.setRuleCode(cleanRequiredText(createDTO.getRuleCode(),"规则编码不能为空"));
        createDTO.setPattern(cleanRequiredText(createDTO.getPattern(),"匹配值不能为空"));
        createDTO.setDescription(cleanOptionalText(createDTO.getDescription()));

        //校验
        validateRuleType(createDTO.getRuleType());
        validateActionType(createDTO.getActionType());
        validateEnabled(createDTO.getEnabled());
        validateLevel(createDTO.getLevel());
        validatePattern(createDTO.getPattern(),createDTO.getRuleType());

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
    @Audited(action = "rule.update")
    public AccessRuleVO update(Long id, AccessRuleUpdateDTO updateDTO) {
        AccessRule entity = accessRuleMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("规则不存在");
        }

        Integer finalRuleType = updateDTO.getRuleType() != null ? updateDTO.getRuleType() : entity.getRuleType();
        String finalPattern = updateDTO.getPattern() != null ? cleanRequiredText(updateDTO.getPattern(), "匹配值不能为空") : entity.getPattern();

        validateRuleType(finalRuleType);
        validatePattern(finalPattern,finalRuleType);

        if (updateDTO.getRuleType() != null) entity.setRuleType(updateDTO.getRuleType());
        if (updateDTO.getPattern() != null) entity.setPattern(cleanRequiredText(updateDTO.getPattern(),"匹配值不能为空"));
        if (updateDTO.getActionType() != null) entity.setActionType(updateDTO.getActionType());
        if (updateDTO.getLevel() != null) entity.setLevel(updateDTO.getLevel());
        if (updateDTO.getEnabled() != null) entity.setEnabled(updateDTO.getEnabled());
        if (updateDTO.getDescription() != null) entity.setDescription(cleanOptionalText(updateDTO.getDescription()));
        accessRuleMapper.updateById(entity);
        accessRuleCache.reload();
        return toVO(accessRuleMapper.selectById(entity.getId()));
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
    @Audited(action = "rule.delete")
    public void delete(Long id) {
        int affected = accessRuleMapper.deleteById(id);
        if (affected == 0) {
            throw new IllegalArgumentException("规则不存在");
        }
        accessRuleCache.reload();
    }

    @Override
    @Audited(action = "rule.toggle")
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


    private String cleanRequiredText(String text,String message) {
        if (StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private String cleanOptionalText(String text) {
        if (StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private void validateRuleType(Integer ruleType) {
        if (ruleType == null || ruleType < 1 || ruleType > 4) {
            throw new IllegalArgumentException("规则类型只能是 1-4");
        }
    }

    private void validateActionType(Integer actionType) {
        if (actionType == null || actionType < 1 || actionType > 3) {
            throw new IllegalArgumentException("动作类型只能是 1-3");
        }
    }

    private void validateEnabled(Integer enabled) {
        if (enabled != null && enabled != 0 && enabled != 1) {
            throw new IllegalArgumentException("启用状态只能是 0 或 1");
        }
    }

    private void validateLevel(Integer level) {
        if (level != null && (level < 1 || level > 3)) {
            throw new IllegalArgumentException("告警等级只能是 1-3");
        }
    }

    private void validatePattern(String pattern, Integer ruleType) {
        if(ruleType ==3) {
            boolean flag = IPV4.pattern().matches(pattern)
                    || IPV4_CIDR.pattern().matches(pattern)
                    || IPV6.pattern().matches(pattern)
                    || IPV6_CIDR.pattern().matches(pattern);
            if (!flag) {
                throw new IllegalArgumentException("匹配值格式不合法，必须为合法IP或者CIDR格式");
            }
        } else if (ruleType == 1 || ruleType == 2 || ruleType == 4) {
            if (!DOMAIN_PATTERN.matcher(pattern).matches()) {
                throw new IllegalArgumentException("匹配值格式不合法，不允许中文、空格或者特殊字符");
            }
        }

    }

}
