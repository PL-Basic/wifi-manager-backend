package com.plagod.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class SupportReviewWorker {

    private static final Log LOGGER =
            LogFactory.getLog(SupportReviewWorker.class);

    private final SupportReviewOutboxTransactionService transactionService;
    private final SupportReviewDispatcher dispatcher;
    private final Clock clock;
    private final int batchSize;
    private final String workerId;

    public SupportReviewWorker(
            SupportReviewOutboxTransactionService transactionService,
            SupportReviewDispatcher dispatcher,
            Clock clock,
            @Value("${wifi.support.review.batch-size:50}") int batchSize,
            @Value("${wifi.support.review.worker-id:}")
            String configuredWorkerId) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException(
                    "Support review 扫描批次必须在 1 到 500 之间");
        }
        if (StringUtils.hasText(configuredWorkerId)
                && configuredWorkerId.length() > 64) {
            throw new IllegalArgumentException(
                    "Support review workerId 不能超过 64 字符");
        }
        this.transactionService = transactionService;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.batchSize = batchSize;
        this.workerId = StringUtils.hasText(configuredWorkerId)
                ? configuredWorkerId
                : "support-" + UUID.randomUUID();
    }

    @Scheduled(
            fixedDelayString = "${wifi.support.review.scan-delay-ms:1000}",
            initialDelayString =
                    "${wifi.support.review.scan-initial-delay-ms:1000}")
    public void dispatchDueReviews() {
        List<Long> eventIds = transactionService.findDispatchableEventIds(
                LocalDateTime.ofInstant(
                        clock.instant(),
                        StableUnits.ASIA_SHANGHAI),
                batchSize);
        for (Long eventId : eventIds) {
            try {
                dispatcher.dispatch(eventId, workerId);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Support review 调度失败，等待 lease 过期后恢复: eventId="
                                + eventId
                                + ", error="
                                + SafeExceptionLogFormatter.format(exception));
            }
        }
    }

    String getWorkerId() {
        return workerId;
    }
}
