package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.audit.Audited;
import com.plagod.dto.ClientLocationPageResult;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.dto.ClientLocationVO;
import com.plagod.entity.ClientLocation;
import com.plagod.mapper.ClientLocationMapper;
import com.plagod.service.ClientLocationService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClientLocationServiceImpl implements ClientLocationService {

    @Autowired
    private ClientLocationMapper clientLocationMapper;

    @Override
    @Audited(action = "location.report")
    public Long report(ClientLocationReportDTO dto, Long userId) {
        ClientLocation entity = new ClientLocation();
        BeanUtils.copyProperties(dto, entity);
        entity.setUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getConsentTime() == null) {
            entity.setConsentTime(now);
        }
        if (entity.getReportTime() == null) {
            entity.setReportTime(now);
        }
        entity.setCreateTime(now);
        clientLocationMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public ClientLocationPageResult pageLocations(long current, long size, String mac, Long userId,
                                                  LocalDateTime startTime, LocalDateTime endTime) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<ClientLocation> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(mac)) {
            queryWrapper.like("mac", mac);
        }
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        if (startTime != null) {
            queryWrapper.ge("report_time", startTime);
        }
        if (endTime != null) {
            queryWrapper.le("report_time", endTime);
        }
        queryWrapper.orderByDesc("report_time");

        Long total = clientLocationMapper.selectCount(queryWrapper);
        Page<ClientLocation> page = clientLocationMapper.selectPage(new Page<>(pageCurrent, pageSize), queryWrapper);
        List<ClientLocationVO> records = new ArrayList<>();
        for (ClientLocation item : page.getRecords()) {
            ClientLocationVO vo = new ClientLocationVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        ClientLocationPageResult result = new ClientLocationPageResult();
        result.setTotal(total == null ? 0 : total);
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }
}
