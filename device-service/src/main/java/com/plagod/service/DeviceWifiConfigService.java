package com.plagod.service;

import com.plagod.dto.device.WifiConfigStageDTO;
import com.plagod.vo.device.WifiConfigTaskVO;

public interface DeviceWifiConfigService {

    WifiConfigTaskVO stageCandidate(String deviceCode, WifiConfigStageDTO stageDTO);
}