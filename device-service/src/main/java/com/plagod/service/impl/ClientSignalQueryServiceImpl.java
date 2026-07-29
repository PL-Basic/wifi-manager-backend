package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.device.ClientSignalRecord;
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

        return page(null, current, size, deviceCode, nodeId, mac, sessionId, state, startTime, endTime);
    }

    @Override
    public ClientSignalPageResult pageOwnedClientSignals(Long ownerUserId, long current, long size, String deviceCode, Long nodeId, String mac, Long sessionId, String state, LocalDateTime startTime, LocalDateTime endTime) {

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少有效用户身份");
        }

        return page(ownerUserId, current, size, deviceCode, nodeId, mac, sessionId, state, startTime, endTime);
    }

    private ClientSignalPageResult page(Long ownerUserId, long current, long size, String deviceCode, Long nodeId, String mac, Long sessionId, String state, LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {

            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<ClientSignalRecord> query = new QueryWrapper<>();

        if (ownerUserId != null) {
            // 认证前 session_id=0 的遥测没有用户归属，不能向普通用户开放。
            query.gt("session_id", 0);
            // 用户归属由 Session 表确定，不能仅依赖请求提供的 MAC 或 sessionId。
            query.apply(
                    "exists (select 1 from t_session owned_session "
                            + "where owned_session.session_id = "
                            + "t_client_signal.session_id "
                            + "and owned_session.user_id = {0})", ownerUserId)
            ;
        }

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

        Page<ClientSignalRecord> resultPage = clientSignalMapper.selectPage(new Page<>(pageCurrent, pageSize), query);

        List<ClientSignalVO> records = new ArrayList<>();

        for (ClientSignalRecord record : resultPage.getRecords()) {
            ClientSignalVO vo = new ClientSignalVO();
            BeanUtils.copyProperties(record, vo);
            records.add(vo);
        }

        ClientSignalPageResult result = new ClientSignalPageResult();
        result.setTotal(resultPage.getTotal());
        result.setCurrent(resultPage.getCurrent());
        result.setSize(resultPage.getSize());
        result.setRecords(records);

        return result;
    }

    @Override
    public boolean wasRecentlyObserved(Long nodeId, String deviceCode, String mac, LocalDateTime sinceTime) {

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