package com.plagod.service.impl;

import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.service.DeviceHeartbeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DeviceHeartbeatServiceImpl implements DeviceHeartbeatService {

    private static final int NODE_OFFLINE = 0;
    private static final int NODE_ONLINE = 1;

    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markTimedOutNodesOffline(LocalDateTime cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("心跳超时截止时间不能为空");
        }

        return esp32NodeMapper.markTimedOutNodesOffline(cutoff, NODE_ONLINE, NODE_OFFLINE);
    }
}