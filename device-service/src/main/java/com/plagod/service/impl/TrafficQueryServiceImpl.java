package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.TrafficLog;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.service.TrafficQueryService;
import com.plagod.vo.device.TrafficLogVO;
import com.plagod.vo.device.TrafficPageResult;
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
    public TrafficPageResult pageTraffic(long current, long size, String mac, Long sessionId, String dstIp, LocalDateTime startTime, LocalDateTime endTime) {

        return page(null, current, size, mac, sessionId, dstIp, startTime, endTime);
    }

    @Override
    public TrafficPageResult pageOwnedTraffic(Long ownerUserId, long current, long size, String mac, Long sessionId, String dstIp, LocalDateTime startTime, LocalDateTime endTime) {

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少有效用户身份");
        }

        return page(ownerUserId, current, size, mac, sessionId, dstIp, startTime, endTime);
    }

    private TrafficPageResult page(Long ownerUserId, long current, long size, String mac, Long sessionId, String dstIp, LocalDateTime startTime, LocalDateTime endTime) {

        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<TrafficLog> query = new QueryWrapper<>();

        if (ownerUserId != null) {
            query.apply("exists (select 1 from t_session owned_session "
                            + "where owned_session.session_id = "
                            + "t_traffic_log.session_id "
                            + "and owned_session.user_id = {0})",
                    ownerUserId);
        }
        if (StringUtils.hasText(mac)) {
            query.like("mac", mac.trim());
        }
        if (sessionId != null) {
            query.eq("session_id", sessionId);
        }
        if (StringUtils.hasText(dstIp)) {
            query.like("dst_ip", dstIp.trim());
        }
        if (startTime != null) {
            query.ge("log_time", startTime);
        }
        if (endTime != null) {
            query.le("log_time", endTime);
        }
        query.orderByDesc("log_time")
                .orderByDesc("id");

        Page<TrafficLog> resultPage = trafficLogMapper.selectPage(
                new Page<>(pageCurrent, pageSize), query);

        List<TrafficLogVO> records = new ArrayList<>();

        for (TrafficLog item : resultPage.getRecords()) {
            TrafficLogVO vo = new TrafficLogVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        TrafficPageResult result = new TrafficPageResult();
        result.setTotal(resultPage.getTotal());
        result.setCurrent(resultPage.getCurrent());
        result.setSize(resultPage.getSize());
        result.setRecords(records);
        return result;
    }
}