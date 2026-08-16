package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
import com.plagod.vo.monitor.AlertEventPageResult;
import com.plagod.vo.monitor.AlertEventVO;
import com.plagod.entity.monitor.AlertEvent;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.security.MonitorTenantScope;
import com.plagod.security.TrustedRequestContext;
import com.plagod.service.AlertEventService;
import com.plagod.support.PageBounds;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlertEventServiceImpl implements AlertEventService {

    @Autowired
    private AlertEventMapper alertEventMapper;

    @Autowired
    private MonitorTenantScope tenantScope;

    @Override
    public AlertEventPageResult pageAlerts(TrustedRequestContext context,
                                           long current, long size, Integer level, Integer status, String mac,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        Long tenantId = tenantScope.requireTenantId(context);
        PageBounds pageBounds = PageBounds.of(
                current <= 0L
                        ? null
                        : (int) Math.min(current, Integer.MAX_VALUE),
                size <= 0L
                        ? null
                        : (int) Math.min(size, Integer.MAX_VALUE));

        QueryWrapper<AlertEvent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("tenant_id", tenantId);
        if (level != null) {
            queryWrapper.eq("level", level);
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        if (StringUtils.hasText(mac)) {
            queryWrapper.like("mac", mac);
        }
        if (startTime != null) {
            queryWrapper.ge("create_time", startTime);
        }
        if (endTime != null) {
            queryWrapper.le("create_time", endTime);
        }
        queryWrapper.orderByDesc("create_time").orderByDesc("id");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AlertEvent> page =
                alertEventMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                                pageBounds.getCurrent(),
                                pageBounds.getSize()),
                        queryWrapper);

        List<AlertEventVO> records = new ArrayList<>();
        for (AlertEvent item : page.getRecords()) {
            records.add(toVO(item));
        }

        AlertEventPageResult result = new AlertEventPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    @Override
    public AlertEventVO getAlert(TrustedRequestContext context, Long id) {
        Long tenantId = tenantScope.requireTenantId(context);
        AlertEvent entity = alertEventMapper.selectByIdAndTenant(
                tenantId,
                id);
        if (entity == null) {
            throw ApiStatusException.notFound("告警事件不存在");
        }
        return toVO(entity);
    }

    @Override
    @Audited(
            action = "alert.handle",
            scope = Audited.Scope.TENANT,
            tenantIdSource = Audited.TenantIdSource.REQUEST)
    public void handle(TrustedRequestContext context, Long id) {
        Long tenantId = tenantScope.requireTenantId(context);
        AlertEvent entity = alertEventMapper.selectByIdAndTenant(
                tenantId,
                id);
        if (entity == null) {
            throw ApiStatusException.notFound("告警事件不存在");
        }
        if (entity.getStatus() != null && entity.getStatus() == 1) {
            throw new IllegalArgumentException("告警事件已处理");
        }
        entity.setStatus(1);
        entity.setHandleUserId(context.getUserId());
        entity.setHandleTime(LocalDateTime.now());
        alertEventMapper.updateById(entity);
    }

    private AlertEventVO toVO(AlertEvent entity) {
        AlertEventVO vo = new AlertEventVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
