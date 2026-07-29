package com.plagod.job;

import com.plagod.service.DeviceHeartbeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "wifi.device", name = "heartbeat-scheduler-enabled", havingValue = "true", matchIfMissing = false)
public class DeviceHeartbeatScheduler {

    @Autowired
    private DeviceHeartbeatService deviceHeartbeatService;

    @Value("${wifi.device.heartbeat-timeout-seconds:60}")
    private long heartbeatTimeoutSeconds;

    @Value("${wifi.device.heartbeat-scan-interval-ms:10000}")
    private long heartbeatScanIntervalMs;

    @PostConstruct
    public void validateConfiguration() {
        if (heartbeatTimeoutSeconds < 10 || heartbeatTimeoutSeconds > 3600) {
            throw new IllegalStateException("设备心跳超时时间必须在 10 到 3600 秒之间");
        }

        if (heartbeatScanIntervalMs < 1000 || heartbeatScanIntervalMs > 300000) {
            throw new IllegalStateException("设备心跳扫描间隔必须在 1000 到 300000 毫秒之间");
        }
    }

    @Scheduled(fixedDelayString = "${wifi.device.heartbeat-scan-interval-ms:10000}", initialDelayString = "${wifi.device.heartbeat-scan-interval-ms:10000}")
    public void markTimedOutNodesOffline() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(heartbeatTimeoutSeconds);

        try {
            int updated = deviceHeartbeatService.markTimedOutNodesOffline(cutoff);

            // 没有节点变化时不打印日志，避免定时任务制造大量无效记录。
            if (updated > 0) {
                log.info("设备心跳超时扫描完成，offlineNodes={}, cutoff={}", updated, cutoff);
            }
        } catch (Exception exception) {
            // 一次扫描失败不能终止后续定时执行。
            log.error("设备心跳超时扫描失败，cutoff={}", cutoff, exception);
        }
    }
}