package com.plagod.service;

import com.plagod.client.DeviceTrafficAnalyticsClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class TrafficAnalyticsService {

    @Autowired
    private DeviceTrafficAnalyticsClient deviceTrafficAnalyticsClient;

    @Value("${wifi.internal.token:}")
    private String internalToken;

    public TrafficAnalyticsSourceVO query(Long userId, String mac, Long sessionId, Long nodeId, String deviceCode, LocalDateTime startTime, LocalDateTime endTime, Integer bucketMinutes, Integer topLimit) {

        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("设备流量数据源当前不可用");
        }

        ApiResponse<TrafficAnalyticsSourceVO> response;

        try {
            response = deviceTrafficAnalyticsClient.queryTraffic(userId, mac, sessionId, nodeId, deviceCode, startTime == null ? null : startTime.toString(), endTime == null ? null : endTime.toString(), bucketMinutes, topLimit, internalToken);
        } catch (FeignException exception) {
            if (exception.status() == 400) {
                throw new IllegalArgumentException("流量统计查询参数无效");
            }
            throw new IllegalStateException("设备流量数据源暂时不可用");
        }

        TrafficAnalyticsSourceVO result = response == null ? null : response.getData();

        if (response == null || response.getCode() != 200 || result == null || result.getSummary() == null) {
            throw new IllegalStateException("设备服务未返回有效流量统计");
        }

        return result;
    }
}