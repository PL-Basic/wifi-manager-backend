package com.plagod.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.monitor.AuditLog;
import com.plagod.mapper.AuditLogMapper;
import com.plagod.service.AuditLogQueryService;
import com.plagod.vo.monitor.AuditLogPageResult;
import com.plagod.vo.monitor.AuditLogVO;
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

        Page<AuditLog> page = auditLogMapper.selectAuditPage(
                new Page<>(pageCurrent, pageSize),
                normalizeFilter(action),
                normalizeFilter(operatorName),
                normalizeFilter(target),
                startTime,
                endTime);

        List<AuditLogVO> records = new ArrayList<>();
        for (AuditLog item : page.getRecords()) {
            records.add(toVO(item));
        }

        AuditLogPageResult result = new AuditLogPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    @Override
    public AuditLogVO getAudit(Long id) {
        AuditLog entity = auditLogMapper.selectAuditById(id);
        if (entity == null) {
            throw new IllegalArgumentException("审计记录不存在");
        }
        return toVO(entity);
    }

    private AuditLogVO toVO(AuditLog entity) {
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
