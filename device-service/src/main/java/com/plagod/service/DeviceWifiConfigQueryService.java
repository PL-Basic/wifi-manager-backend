package com.plagod.service;

import com.plagod.vo.device.WifiConfigTaskVO;

public interface DeviceWifiConfigQueryService {

    WifiConfigTaskVO getTask(Long tenantId, String deviceCode, String requestId);

    WifiConfigTaskVO getLatestTask(Long tenantId, String deviceCode);
}
