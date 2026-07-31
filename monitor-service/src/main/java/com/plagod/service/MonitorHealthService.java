package com.plagod.service;

import com.plagod.mapper.MonitorHealthMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorHealthService {

    @Autowired
    private MonitorHealthMapper monitorHealthMapper;

    @Transactional(readOnly = true)
    public String check() {

        Integer result = monitorHealthMapper.ping();

        if (!Integer.valueOf(1).equals(result)) {
            throw new IllegalStateException("Monitor 数据库健康检查失败");
        }

        return "UP";
    }
}