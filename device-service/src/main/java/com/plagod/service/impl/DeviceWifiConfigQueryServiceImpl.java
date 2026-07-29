package com.plagod.service.impl;

import com.plagod.constant.DeviceWifiConfigStatus;
import com.plagod.entity.DeviceWifiConfigRecord;
import com.plagod.mapper.DeviceWifiConfigRecordMapper;
import com.plagod.service.DeviceWifiConfigQueryService;
import com.plagod.vo.device.WifiConfigTaskVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceWifiConfigQueryServiceImpl implements DeviceWifiConfigQueryService {

    @Autowired
    private DeviceWifiConfigRecordMapper wifiConfigRecordMapper;

    @Override
    public WifiConfigTaskVO getTask(String deviceCode, String requestId) {

        String cleanDeviceCode = cleanRequired(deviceCode, 64, "deviceCode 不能为空");
        String cleanRequestId = cleanRequired(requestId, 64, "requestId 不能为空");

        DeviceWifiConfigRecord record = wifiConfigRecordMapper.selectByDeviceCodeAndRequestId(cleanDeviceCode, cleanRequestId);

        if (record == null || !cleanDeviceCode.equals(record.getDeviceCode()) || !cleanRequestId.equals(record.getRequestId())) {
            throw new IllegalArgumentException("候选 WiFi 配置任务不存在");
        }

        WifiConfigTaskVO vo = new WifiConfigTaskVO();
        BeanUtils.copyProperties(record, vo);
        vo.setStatusName(DeviceWifiConfigStatus.nameOf(record.getStatus()));
        return vo;
    }

    private String cleanRequired(String value, int maxLength, String message) {

        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }

        String cleaned = value.trim();
        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException(message + "，长度超限");
        }
        return cleaned;
    }
}