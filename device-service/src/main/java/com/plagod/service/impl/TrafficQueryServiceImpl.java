package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.vo.TrafficLogVO;
import com.plagod.vo.TrafficPageResult;
import com.plagod.entity.TrafficLog;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.service.TrafficQueryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrafficQueryServiceImpl implements TrafficQueryService {

    @Autowired
    private TrafficLogMapper trafficLogMapper;

    @Override
    public TrafficPageResult pageTraffic(long current, long size, String mac, Long sessionId, String dstIp,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<TrafficLog> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(mac)) {
            queryWrapper.like("mac", mac);
        }
        if (sessionId != null) {
            queryWrapper.eq("session_id", sessionId);
        }
        if (StringUtils.hasText(dstIp)) {
            queryWrapper.like("dst_ip", dstIp);
        }
        if (startTime != null) {
            queryWrapper.ge("log_time", startTime);
        }
        if (endTime != null) {
            queryWrapper.le("log_time", endTime);
        }
        queryWrapper.orderByDesc("log_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TrafficLog> page =
                trafficLogMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageCurrent, pageSize), queryWrapper);

        List<TrafficLogVO> records = new ArrayList<>();
        for (TrafficLog item : page.getRecords()) {
            TrafficLogVO vo = new TrafficLogVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        TrafficPageResult result = new TrafficPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }
}
