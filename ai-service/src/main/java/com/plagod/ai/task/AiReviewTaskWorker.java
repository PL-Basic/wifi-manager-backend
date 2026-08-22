package com.plagod.ai.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class AiReviewTaskWorker {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AiReviewTaskWorker.class);

    private final AiReviewTaskTransactionService transactionService;
    private final AiReviewTaskDispatcher dispatcher;
    private final int batchSize;
    private final String workerId;

    @Autowired
    public AiReviewTaskWorker(
            AiReviewTaskTransactionService transactionService,
            AiReviewTaskDispatcher dispatcher,
            @Value("${wifi.ai.task.batch-size:50}")
            int batchSize) {
        this(
                transactionService,
                dispatcher,
                batchSize,
                "ai-" + UUID.randomUUID());
    }

    AiReviewTaskWorker(
            AiReviewTaskTransactionService transactionService,
            AiReviewTaskDispatcher dispatcher,
            int batchSize,
            String workerId) {
        this.transactionService = Objects.requireNonNull(
                transactionService,
                "transactionService 不能为空");
        this.dispatcher = Objects.requireNonNull(
                dispatcher,
                "dispatcher 不能为空");
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException(
                    "AI worker batchSize 必须在 1 到 500 之间");
        }
        if (workerId == null
                || workerId.trim().isEmpty()
                || workerId.length() > 64) {
            throw new IllegalArgumentException(
                    "AI workerId 必须为 1 到 64 字符");
        }
        this.batchSize = batchSize;
        this.workerId = workerId;
    }

    @Scheduled(
            fixedDelayString = "${wifi.ai.task.scan-interval-ms:1000}",
            initialDelayString = "${wifi.ai.task.scan-initial-delay-ms:1000}")
    public void dispatchDueTasks() {
        List<Long> reviewTaskIds;
        try {
            reviewTaskIds = transactionService.findDispatchableIds(
                    LocalDateTime.now(),
                    batchSize);
        } catch (RuntimeException exception) {
            LOGGER.warn("AI review task scan failed");
            return;
        }
        if (reviewTaskIds == null) {
            reviewTaskIds = Collections.emptyList();
        }

        for (Long reviewTaskId : reviewTaskIds) {
            try {
                dispatcher.dispatchOne(reviewTaskId, workerId);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "AI review task dispatch failed: reviewTaskId={}",
                        reviewTaskId);
            }
        }
    }
}
