package com.plagod.ai.task;

import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class AiReviewTaskDispatcher {

    private static final String INPUT_CONFLICT =
            "AI_TASK_INPUT_CONFLICT";
    private static final String PROVIDER_UNAVAILABLE =
            "AI_PROVIDER_UNAVAILABLE";
    private static final String RESPONSE_INVALID =
            "AI_RESPONSE_INVALID";

    private final AiReviewTaskTransactionService transactionService;
    private final AiReviewTaskInputLoader inputLoader;
    private final AiProviderTransactionBoundary providerBoundary;
    private final int maxAttempts;
    private final long leaseSeconds;
    private final long retryDelaySeconds;
    private final double minimumConfidence;

    public AiReviewTaskDispatcher(
            AiReviewTaskTransactionService transactionService,
            AiReviewTaskInputLoader inputLoader,
            AiProviderTransactionBoundary providerBoundary,
            @Value("${wifi.ai.task.max-attempts:5}")
            int maxAttempts,
            @Value("${wifi.ai.task.lease-seconds:30}")
            long leaseSeconds,
            @Value("${wifi.ai.task.retry-delay-seconds:10}")
            long retryDelaySeconds,
            @Value("${wifi.ai.task.minimum-confidence:0.8}")
            double minimumConfidence) {
        this.transactionService = Objects.requireNonNull(
                transactionService,
                "transactionService 不能为空");
        this.inputLoader = Objects.requireNonNull(
                inputLoader,
                "inputLoader 不能为空");
        this.providerBoundary = Objects.requireNonNull(
                providerBoundary,
                "providerBoundary 不能为空");
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException(
                    "AI 任务最大尝试次数必须在 1 到 100 之间");
        }
        if (leaseSeconds < 1 || retryDelaySeconds < 1) {
            throw new IllegalArgumentException(
                    "AI 任务 lease 和重试间隔必须大于 0");
        }
        if (Double.isNaN(minimumConfidence)
                || Double.isInfinite(minimumConfidence)
                || minimumConfidence < 0.0d
                || minimumConfidence > 1.0d) {
            throw new IllegalArgumentException(
                    "minimumConfidence 必须在 0 到 1 之间");
        }
        this.maxAttempts = maxAttempts;
        this.leaseSeconds = leaseSeconds;
        this.retryDelaySeconds = retryDelaySeconds;
        this.minimumConfidence = minimumConfidence;
    }

    public AiReviewTaskDispatchOutcome dispatchOne(
            Long reviewTaskId,
            String workerId) {
        LocalDateTime claimedAt = LocalDateTime.now();
        AiReviewTaskClaim claim = transactionService.claim(
                reviewTaskId,
                workerId,
                claimedAt,
                claimedAt.plusSeconds(leaseSeconds),
                maxAttempts);
        if (claim == null) {
            return AiReviewTaskDispatchOutcome.NOT_CLAIMED;
        }

        AiModerationRequest request;
        try {
            request = inputLoader.load(claim);
        } catch (AiReviewTaskInputException exception) {
            return transactionService.completeResult(
                    claim,
                    AiModerationResult.manual(INPUT_CONFLICT),
                    LocalDateTime.now());
        }

        AiModerationResult result;
        try {
            result = providerBoundary.review(
                    request,
                    minimumConfidence);
        } catch (RuntimeException exception) {
            return completeFailure(claim, PROVIDER_UNAVAILABLE);
        }

        if (isRetryable(result)) {
            return completeFailure(claim, result.getReasonCode());
        }
        return transactionService.completeResult(
                claim,
                result,
                LocalDateTime.now());
    }

    private boolean isRetryable(AiModerationResult result) {
        return result != null
                && (PROVIDER_UNAVAILABLE.equals(result.getReasonCode())
                || RESPONSE_INVALID.equals(result.getReasonCode()));
    }

    private AiReviewTaskDispatchOutcome completeFailure(
            AiReviewTaskClaim claim,
            String errorCode) {
        LocalDateTime failedAt = LocalDateTime.now();
        return transactionService.completeFailure(
                claim,
                errorCode,
                failedAt,
                failedAt.plusSeconds(retryDelaySeconds),
                maxAttempts);
    }
}
