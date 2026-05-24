package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
import com.plagod.dto.AlertEventPageResult;
import com.plagod.dto.AlertEventVO;
import com.plagod.entity.AlertEvent;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.service.AlertEventService;
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

    @Override
    public AlertEventPageResult pageAlerts(long current, long size, Integer level, Integer status, String mac,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<AlertEvent> queryWrapper = new QueryWrapper<>();
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
        queryWrapper.orderByDesc("create_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AlertEvent> page =
                alertEventMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageCurrent, pageSize), queryWrapper);

        List<AlertEventVO> records = new ArrayList<>();
        for (AlertEvent item : page.getRecords()) {
            AlertEventVO vo = new AlertEventVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        AlertEventPageResult result = new AlertEventPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    @Override
    @Audited(action = "alert.handle")
    public void handle(Long id, Long handleUserId) {
        AlertEvent entity = alertEventMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("告警事件不存在");
        }
        if (entity.getStatus() != null && entity.getStatus() == 1) {
            throw new IllegalArgumentException("告警事件已处理");
        }
        entity.setStatus(1);
        entity.setHandleUserId(handleUserId);
        entity.setHandleTime(LocalDateTime.now());
        alertEventMapper.updateById(entity);
    }
}
