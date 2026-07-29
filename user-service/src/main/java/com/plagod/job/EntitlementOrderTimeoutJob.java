package com.plagod.job;

import com.plagod.service.EntitlementOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EntitlementOrderTimeoutJob {

    @Autowired
    private EntitlementOrderService orderService;

    @Scheduled(
            fixedDelayString = "${wifi.entitlement.close-delay-ms:60000}")
    public void closeExpiredOrders() {
        try {
            int closed = orderService.closeExpiredOrders(100);

            if (closed > 0) {
                log.info("closed {} expired entitlement orders", closed);
            }
        } catch (Exception exception) {
            log.error("close expired entitlement orders failed", exception);
        }
    }
}