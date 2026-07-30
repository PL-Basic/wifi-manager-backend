package com.plagod.controller;

import com.plagod.client.MonitorServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import com.plagod.vo.monitor.AlertRuleAnalyticsVO;
import com.plagod.vo.monitor.SignalAnalysisVO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@RestController
@RequestMapping("/admin/analytics")
public class AdminAnalyticsController {

    @Autowired
    private MonitorServiceClient monitorServiceClient;

    @GetMapping("/signals")
    public ApiResponse<SignalAnalysisVO> querySignals(@RequestParam Long nodeId,
                                                      @RequestParam String mac,
                                                      @RequestParam String startTime,
                                                      @RequestParam String endTime, @RequestParam(defaultValue = "31") Integer sampleLimit,
                                                      @RequestParam(defaultValue = "5") Integer bucketMinutes) {

        return callAnalytics(() -> monitorServiceClient.querySignalAnalytics(nodeId, mac, startTime, endTime, sampleLimit, bucketMinutes));
    }

    @GetMapping("/traffic")
    public ApiResponse<TrafficAnalyticsSourceVO> queryTraffic(@RequestParam(required = false) Long userId,
                                                              @RequestParam(required = false) String mac,
                                                              @RequestParam(required = false) Long sessionId,
                                                              @RequestParam(required = false) Long nodeId,
                                                              @RequestParam(required = false) String deviceCode,
                                                              @RequestParam String startTime,
                                                              @RequestParam String endTime,
                                                              @RequestParam(defaultValue = "60") Integer bucketMinutes,
                                                              @RequestParam(defaultValue = "10") Integer topLimit) {

        return callAnalytics(() -> monitorServiceClient.queryTrafficAnalytics(userId, mac, sessionId, nodeId, deviceCode, startTime, endTime, bucketMinutes, topLimit));
    }


    @GetMapping("/alerts-rules")
    public ApiResponse<AlertRuleAnalyticsVO> queryAlertRules(@RequestParam(required = false) Long userId,
                                                             @RequestParam(required = false) String mac,
                                                             @RequestParam(required = false) Long sessionId,
                                                             @RequestParam(required = false) Long nodeId,
                                                             @RequestParam(required = false) String deviceCode,
                                                             @RequestParam String startTime,
                                                             @RequestParam String endTime,
                                                             @RequestParam(defaultValue = "10") Integer topLimit) {

        return callAnalytics(() -> monitorServiceClient.queryAlertRuleAnalytics(userId, mac, sessionId, nodeId, deviceCode, startTime, endTime, topLimit));
    }

    private <T> ApiResponse<T> callAnalytics(Supplier<ApiResponse<T>> request) {
        try {
            return request.get();
        } catch (FeignException exception) {
            if (exception.status() == 400) {
                // 下游已经确认是查询参数错误，对外不能伪装成服务器故障。
                throw new IllegalArgumentException("统计查询参数无效");
            }
            throw exception;
        }
    }

}