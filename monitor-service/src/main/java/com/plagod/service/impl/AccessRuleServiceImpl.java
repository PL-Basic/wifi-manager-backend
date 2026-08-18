package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.AuditDetail;
import com.plagod.audit.AuditTargetId;
import com.plagod.audit.Audited;
import com.plagod.dto.monitor.AccessRuleCreateDTO;
import com.plagod.vo.monitor.AccessRulePageResult;
import com.plagod.dto.monitor.AccessRuleUpdateDTO;
import com.plagod.vo.monitor.AccessRuleVO;
import com.plagod.entity.monitor.AccessRule;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.AccessRuleMapper;
import com.plagod.security.MonitorTenantScope;
import com.plagod.security.MonitorTrustedRequestContextProvider;
import com.plagod.service.AccessRuleCache;
import com.plagod.service.AccessRuleService;
import com.plagod.support.PageBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AccessRuleServiceImpl implements AccessRuleService {

    private static final Logger log =
            LoggerFactory.getLogger(AccessRuleServiceImpl.class);

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

    @Autowired
    private MonitorTrustedRequestContextProvider contextProvider;

    @Autowired
    private MonitorTenantScope tenantScope;

    @Autowired
    private HttpServletRequest request;

    @Override
    @Audited(
            action = "rule.create",
            targetType = "ACCESS_RULE",
            scope = Audited.Scope.CONTEXT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    @Transactional(rollbackFor = Exception.class)
    public AccessRuleVO create(AccessRuleCreateDTO createDTO) {
        Long tenantId = requireCurrentTenantId();
        createDTO.setRuleCode(cleanRequiredText(createDTO.getRuleCode(),"规则编码不能为空"));
        createDTO.setPattern(cleanRequiredText(createDTO.getPattern(),"匹配值不能为空"));
        createDTO.setDescription(cleanOptionalText(createDTO.getDescription()));

        //校验
        validateRuleType(createDTO.getRuleType());
        validateActionType(createDTO.getActionType());
        validateEnabled(createDTO.getEnabled());
        validateLevel(createDTO.getLevel());
        validatePattern(createDTO.getPattern(),createDTO.getRuleType());

        if (accessRuleMapper.selectByCodeAndTenant(
                tenantId,
                createDTO.getRuleCode()) != null) {
            throw new IllegalArgumentException("规则编码已存在");
        }

        AccessRule entity = new AccessRule();
        BeanUtils.copyProperties(createDTO, entity);
        entity.setTenantId(tenantId);
        if (entity.getLevel() == null) {
            entity.setLevel(2);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (accessRuleMapper.insert(entity) != 1 || entity.getId() == null) {
            throw new IllegalStateException("规则创建失败");
        }
        refreshCacheAfterCommit();
        return toVO(requireRule(tenantId, entity.getId(), false));
    }

    @Override
    @Audited(
            action = "rule.update",
            targetType = "ACCESS_RULE",
            scope = Audited.Scope.CONTEXT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    @Transactional(rollbackFor = Exception.class)
    public AccessRuleVO update(@AuditTargetId Long id,
                               AccessRuleUpdateDTO updateDTO) {
        Long tenantId = requireCurrentTenantId();
        AccessRule entity = requireRule(tenantId, id, true);

        Integer finalEnabled = updateDTO.getEnabled() == null ? entity.getEnabled() : updateDTO.getEnabled();
        Integer finalActionType = updateDTO.getActionType() == null ? entity.getActionType() : updateDTO.getActionType();
        Integer finalLevel = updateDTO.getLevel() != null ? updateDTO.getLevel() : entity.getLevel();
        Integer finalRuleType = updateDTO.getRuleType() != null ? updateDTO.getRuleType() : entity.getRuleType();
        String finalPattern = updateDTO.getPattern() != null ? cleanRequiredText(updateDTO.getPattern(), "匹配值不能为空") : entity.getPattern();

        validateEnabled(finalEnabled);
        validateActionType(finalActionType);
        validateLevel(finalLevel);
        validateRuleType(finalRuleType);
        validatePattern(finalPattern,finalRuleType);

        if (updateDTO.getRuleType() != null) entity.setRuleType(updateDTO.getRuleType());
        if (updateDTO.getPattern() != null) entity.setPattern(cleanRequiredText(updateDTO.getPattern(),"匹配值不能为空"));
        if (updateDTO.getActionType() != null) entity.setActionType(updateDTO.getActionType());
        if (updateDTO.getLevel() != null) entity.setLevel(updateDTO.getLevel());
        if (updateDTO.getEnabled() != null) entity.setEnabled(updateDTO.getEnabled());
        if (updateDTO.getDescription() != null) entity.setDescription(cleanOptionalText(updateDTO.getDescription()));
        if (accessRuleMapper.updateById(entity) != 1) {
            throw ApiStatusException.conflict("规则更新冲突");
        }
        refreshCacheAfterCommit();
        return toVO(requireRule(tenantId, entity.getId(), false));
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
    @Audited(
            action = "rule.delete",
            targetType = "ACCESS_RULE",
            scope = Audited.Scope.CONTEXT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    @Transactional(rollbackFor = Exception.class)
    public void delete(@AuditTargetId Long id) {
        Long tenantId = requireCurrentTenantId();
        AccessRule entity = requireRule(tenantId, id, true);
        Integer expectedVersion = requireVersion(entity.getVersion());
        QueryWrapper<AccessRule> delete = new QueryWrapper<>();
        delete.eq("tenant_id", tenantId)
                .eq("id", id)
                .eq("version", expectedVersion)
                .eq("del_flag", 0);
        if (accessRuleMapper.delete(delete) != 1) {
            AccessRule current = accessRuleMapper.selectByIdAndTenant(
                    tenantId,
                    id);
            if (current == null) {
                throw ApiStatusException.notFound("规则不存在");
            }
            throw ApiStatusException.conflict(
                    !expectedVersion.equals(current.getVersion())
                            ? "规则版本冲突"
                            : "规则并发删除冲突");
        }
        refreshCacheAfterCommit();
    }

    @Override
    @Audited(
            action = "rule.toggle",
            targetType = "ACCESS_RULE",
            scope = Audited.Scope.CONTEXT,
            tenantIdSource = Audited.TenantIdSource.REQUEST,
            recordDenied = true,
            recordFailed = true)
    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(@AuditTargetId Long id,
                              @AuditDetail("enabled") Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new IllegalArgumentException("enabled 只能是 0 或 1");
        }
        Long tenantId = requireCurrentTenantId();
        AccessRule entity = requireRule(tenantId, id, true);
        int currentEnabled = requireStoredEnabled(entity.getEnabled());
        if (enabled == currentEnabled) {
            return;
        }
        Integer expectedVersion = requireVersion(entity.getVersion());
        int affected = accessRuleMapper.updateEnabledByTenantAndVersion(
                tenantId,
                id,
                enabled,
                expectedVersion);
        if (affected != 1) {
            AccessRule current = accessRuleMapper.selectByIdAndTenant(
                    tenantId,
                    id);
            if (current == null) {
                throw ApiStatusException.notFound("规则不存在");
            }
            if (!expectedVersion.equals(current.getVersion())) {
                throw ApiStatusException.conflict("规则版本冲突");
            }
            throw ApiStatusException.conflict("规则并发更新冲突");
        }
        refreshCacheAfterCommit();
    }

    @Override
    public AccessRulePageResult pageRules(long current, long size, Integer ruleType, Integer enabled, String keyword) {
        PageBounds pageBounds = PageBounds.of(
                current <= 0L
                        ? null
                        : (int) Math.min(current, Integer.MAX_VALUE),
                size <= 0L
                        ? null
                        : (int) Math.min(size, Integer.MAX_VALUE));

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
        queryWrapper.orderByDesc("create_time").orderByDesc("id");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AccessRule> page =
                accessRuleMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                                pageBounds.getCurrent(),
                                pageBounds.getSize()),
                        queryWrapper);

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

    private AccessRule requireRule(
            Long tenantId,
            Long id,
            boolean forUpdate) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("规则ID无效");
        }
        AccessRule entity = forUpdate
                ? accessRuleMapper.selectByIdAndTenantForUpdate(tenantId, id)
                : accessRuleMapper.selectByIdAndTenant(tenantId, id);
        if (entity == null || Integer.valueOf(1).equals(entity.getDelFlag())) {
            throw ApiStatusException.notFound("规则不存在");
        }
        return entity;
    }

    private Long requireCurrentTenantId() {
        return tenantScope.requireTenantId(
                contextProvider.resolve(request));
    }

    private Integer requireVersion(Integer version) {
        if (version == null || version < 0) {
            throw ApiStatusException.conflict("规则版本无效");
        }
        return version;
    }

    private int requireStoredEnabled(Integer enabled) {
        if (!Integer.valueOf(0).equals(enabled)
                && !Integer.valueOf(1).equals(enabled)) {
            throw ApiStatusException.conflict("规则启停状态无效");
        }
        return enabled;
    }

    private void refreshCacheAfterCommit() {
        if (!TransactionSynchronizationManager
                .isActualTransactionActive()
                || !TransactionSynchronizationManager
                .isSynchronizationActive()) {
            accessRuleCache.reload();
            return;
        }
        try {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            reloadCacheAfterCommitSafely();
                        }
                    });
        } catch (RuntimeException exception) {
            log.warn("访问规则缓存提交后刷新注册失败");
        }
    }

    private void reloadCacheAfterCommitSafely() {
        try {
            accessRuleCache.reload();
        } catch (RuntimeException exception) {
            log.warn("访问规则缓存提交后刷新失败");
        }
    }


    private String cleanRequiredText(String text,String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private String cleanOptionalText(String text) {
        if (!StringUtils.hasText(text)) {
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
            boolean flag = IPV4.matcher(pattern).matches()
                    || IPV4_CIDR.matcher(pattern).matches()
                    || IPV6.matcher(pattern).matches()
                    || IPV6_CIDR.matcher(pattern).matches();
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
