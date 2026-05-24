package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.dto.AuditLogPageResult;
import com.plagod.dto.AuditLogVO;
import com.plagod.entity.AuditLog;
import com.plagod.mapper.AuditLogMapper;
import com.plagod.service.AuditLogQueryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Override
    public AuditLogPageResult pageAudits(long current, long size, String action, String operatorName, String target,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<AuditLog> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(action)) {
            queryWrapper.like("action", action);
        }
        if (StringUtils.hasText(operatorName)) {
            queryWrapper.like("operator_name", operatorName);
        }
        if (StringUtils.hasText(target)) {
            queryWrapper.like("target", target);
        }
        if (startTime != null) {
            queryWrapper.ge("create_time", startTime);
        }
        if (endTime != null) {
            queryWrapper.le("create_time", endTime);
        }
        queryWrapper.orderByDesc("create_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AuditLog> page =
                auditLogMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageCurrent, pageSize), queryWrapper);

        List<AuditLogVO> records = new ArrayList<>();
        for (AuditLog item : page.getRecords()) {
            AuditLogVO vo = new AuditLogVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        AuditLogPageResult result = new AuditLogPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }
}
