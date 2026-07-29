package com.plagod.service;

import com.plagod.vo.device.WifiConfigTaskVO;

public interface DeviceWifiConfigQueryService {

    WifiConfigTaskVO getTask(String deviceCode, String requestId);
}