package com.plagod.service.impl;

import com.plagod.constant.DeviceWifiConfigStatus;
import com.plagod.entity.device.DeviceWifiConfigRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.DeviceWifiConfigRecordMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.service.DeviceWifiConfigQueryService;
import com.plagod.vo.device.WifiConfigTaskVO;
import com.plagod.utils.TenantScopeUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceWifiConfigQueryServiceImpl implements DeviceWifiConfigQueryService {

    @Autowired
    private DeviceWifiConfigRecordMapper wifiConfigRecordMapper;
    @Autowired
    private Esp32NodeMapper esp32NodeMapper;

    @Override
    public WifiConfigTaskVO getTask(Long tenantId, String deviceCode, String requestId) {
        TenantScopeUtils.requireTenantId(tenantId);

        String cleanDeviceCode = cleanRequired(deviceCode, 64, "deviceCode 不能为空");
        String cleanRequestId = cleanRequired(requestId, 64, "requestId 不能为空");

        DeviceWifiConfigRecord record = wifiConfigRecordMapper.selectByDeviceCodeAndRequestId(
                tenantId, cleanDeviceCode, cleanRequestId);

        if (record == null || !cleanDeviceCode.equals(record.getDeviceCode()) || !cleanRequestId.equals(record.getRequestId())) {
            throw ApiStatusException.notFound("候选 WiFi 配置任务不存在");
        }

        return toVO(record);
    }

    @Override
    public WifiConfigTaskVO getLatestTask(Long tenantId, String deviceCode) {
        TenantScopeUtils.requireTenantId(tenantId);

        String cleanDeviceCode = cleanRequired(deviceCode, 64, "deviceCode 不能为空");
        Esp32Node node = esp32NodeMapper.selectByDeviceCodeAndTenantIncludeDeleted(
                tenantId, cleanDeviceCode);

        if (node == null || !cleanDeviceCode.equals(node.getDeviceCode())) {
            throw ApiStatusException.notFound("目标 ESP32 不存在");
        }

        DeviceWifiConfigRecord record = wifiConfigRecordMapper.selectLatestByNodeId(
                tenantId, node.getNodeId());
        return record == null ? null : toVO(record);
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

    private WifiConfigTaskVO toVO(DeviceWifiConfigRecord record) {
        WifiConfigTaskVO vo = new WifiConfigTaskVO();
        BeanUtils.copyProperties(record, vo);
        vo.setStatusName(DeviceWifiConfigStatus.nameOf(record.getStatus()));
        return vo;
    }
}
