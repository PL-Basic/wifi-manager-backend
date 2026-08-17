package com.plagod.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class MarketplaceFulfillmentWorker {

    private static final Log LOGGER =
            LogFactory.getLog(MarketplaceFulfillmentWorker.class);

    private final MarketplaceFulfillmentTransactionService transactionService;
    private final MarketplaceFulfillmentDispatcher dispatcher;
    private final int batchSize;
    private final String workerId;

    public MarketplaceFulfillmentWorker(
            MarketplaceFulfillmentTransactionService transactionService,
            MarketplaceFulfillmentDispatcher dispatcher,
            @Value("${wifi.marketplace.fulfillment.batch-size:50}")
            int batchSize,
            @Value("${wifi.marketplace.fulfillment.worker-id:}")
            String configuredWorkerId) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException(
                    "履约扫描批次必须在 1 到 500 之间");
        }
        if (StringUtils.hasText(configuredWorkerId)
                && configuredWorkerId.length() > 64) {
            throw new IllegalArgumentException(
                    "履约 workerId 不能超过 64 字符");
        }
        this.transactionService = transactionService;
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
        this.workerId = StringUtils.hasText(configuredWorkerId)
                ? configuredWorkerId
                : "market-" + UUID.randomUUID();
    }

    @Scheduled(
            fixedDelayString =
                    "${wifi.marketplace.fulfillment.scan-delay-ms:1000}",
            initialDelayString =
                    "${wifi.marketplace.fulfillment.scan-initial-delay-ms:1000}")
    public void dispatchDueFulfillments() {
        List<Long> fulfillmentIds =
                transactionService.findDispatchableIds(
                        LocalDateTime.now(),
                        batchSize);
        for (Long fulfillmentId : fulfillmentIds) {
            try {
                dispatcher.dispatchOne(
                        fulfillmentId,
                        workerId);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "履约调度失败，等待 lease 过期后恢复: fulfillmentId="
                                + fulfillmentId,
                        exception);
            }
        }
    }

    String getWorkerId() {
        return workerId;
    }
}
