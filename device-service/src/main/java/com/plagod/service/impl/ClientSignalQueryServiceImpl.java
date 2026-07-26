package com.plagod.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.ClientSignalRecord;
import com.plagod.mapper.ClientSignalMapper;
import com.plagod.service.ClientSignalQueryService;
import com.plagod.vo.device.ClientSignalPageResult;
import com.plagod.vo.device.ClientSignalVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ClientSignalQueryServiceImpl implements ClientSignalQueryService {

    @Autowired
    private ClientSignalMapper clientSignalMapper;

    @Override
    public ClientSignalPageResult pageClientSignals(long current, long size, String deviceCode, Long nodeId, String mac, Long sessionId, String state, LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<ClientSignalRecord> query = new QueryWrapper<>();
        if (StringUtils.hasText(deviceCode)) {
            query.eq("device_code", deviceCode.trim());
        }

        if (nodeId != null) {
            query.eq("node_id", nodeId);
        }

        if (StringUtils.hasText(mac)) {
            query.like("mac", mac.trim());
        }

        if (sessionId != null) {
            query.eq("session_id", sessionId);
        }

        if (StringUtils.hasText(state)) {
            query.eq("state", state.trim().toUpperCase(Locale.ROOT));
        }

        if (startTime != null) {
            query.ge("report_time", startTime);
        }

        if (endTime != null) {
            query.le("report_time", endTime);
        }

        query.orderByDesc("report_time")
                .orderByDesc("id");

        Page<ClientSignalRecord> page = clientSignalMapper.selectPage(new Page<>(pageCurrent, pageSize), query);

        List<ClientSignalVO> records = new ArrayList<>();
        for (ClientSignalRecord record : page.getRecords()) {
            ClientSignalVO vo = new ClientSignalVO();
            BeanUtils.copyProperties(record, vo);
            records.add(vo);
        }

        ClientSignalPageResult result = new ClientSignalPageResult();

        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);

        return result;
    }

    @Override
    public boolean wasRecentlyObserved(Long nodeId, String deviceCode, String mac, LocalDateTime sinceTime) {
        // 参数不完整时不能把未知客户端误判成已观察客户端。
        if (nodeId == null || nodeId <= 0 || !StringUtils.hasText(deviceCode) || !StringUtils.hasText(mac) || sinceTime == null) {
            return false;
        }

        Number count = clientSignalMapper.selectCount(
                new QueryWrapper<ClientSignalRecord>()
                        .eq("node_id", nodeId)
                        .eq("device_code", deviceCode.trim())
                        .eq("mac", mac.trim().toUpperCase(Locale.ROOT))
                        .ge("report_time", sinceTime)
        );
        return count != null && count.longValue() > 0;
    }
}