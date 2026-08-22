package com.plagod.service.impl;

import com.plagod.client.DeviceLocationSessionClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.LocationSessionContextVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceLocationSessionGateway {

    @Autowired
    private DeviceLocationSessionClient deviceLocationSessionClient;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ApiResponse<LocationSessionContextVO> getLocationContext(
            Long sessionId) {
        return deviceLocationSessionClient.getLocationContext(sessionId);
    }
}
