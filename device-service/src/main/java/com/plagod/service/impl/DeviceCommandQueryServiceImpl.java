package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.service.DeviceCommandQueryService;
import com.plagod.vo.device.DeviceCommandPageResult;
import com.plagod.vo.device.DeviceCommandVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DeviceCommandQueryServiceImpl implements DeviceCommandQueryService {

    @Autowired
    private DeviceCommandRecordMapper deviceCommandRecordMapper;

    @Override
    public DeviceCommandPageResult pageCommands(long current, long size, String requestId, String deviceCode, String commandType, String purpose, Integer status, Long sessionId, String mac) {

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<DeviceCommandRecord> query = new QueryWrapper<>();
        if (StringUtils.hasText(requestId)) {
            query.eq("request_id", requestId.trim());
        }
        if (StringUtils.hasText(deviceCode)) {
            query.eq("device_code", deviceCode.trim());
        }
        if (StringUtils.hasText(commandType)) {
            query.eq("command_type", commandType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(purpose)) {
            query.eq("purpose", purpose.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null) {
            query.eq("status", status);
        }
        if (sessionId != null) {
            query.eq("session_id", sessionId);
        }
        if (StringUtils.hasText(mac)) {
            query.eq("mac", mac.trim().toUpperCase(Locale.ROOT));
        }

        query.orderByDesc("command_id");

        Page<DeviceCommandRecord> page = deviceCommandRecordMapper.selectPage(new Page<>(pageCurrent, pageSize), query);

        List<DeviceCommandVO> records = new ArrayList<>();

        for (DeviceCommandRecord command : page.getRecords()) {

            DeviceCommandVO vo = new DeviceCommandVO();
            BeanUtils.copyProperties(command, vo);
            records.add(vo);
        }

        DeviceCommandPageResult result = new DeviceCommandPageResult();

        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }
}