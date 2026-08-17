package com.plagod.support;

import com.plagod.exception.ApiErrorKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class SupportReviewDispatcher {

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final SupportReviewOutboxTransactionService transactionService;
    private final SupportReviewRemoteGateway remoteGateway;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration retryDelay;
    private final int maxAttempts;

    @Autowired
    public SupportReviewDispatcher(
            SupportReviewOutboxTransactionService transactionService,
            SupportReviewRemoteGateway remoteGateway,
            Clock clock) {
        this(
                transactionService,
                remoteGateway,
                clock,
                DEFAULT_LEASE_DURATION,
                DEFAULT_RETRY_DELAY,
                DEFAULT_MAX_ATTEMPTS);
    }

    SupportReviewDispatcher(
            SupportReviewOutboxTransactionService transactionService,
            SupportReviewRemoteGateway remoteGateway,
            Clock clock,
            Duration leaseDuration,
            Duration retryDelay,
            int maxAttempts) {
        this.transactionService = transactionService;
        this.remoteGateway = remoteGateway;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    public SupportReviewDispatchResult dispatch(Long eventId, String workerId) {
        LocalDateTime claimTime = now();
        SupportReviewClaim claim = transactionService.claim(
                eventId,
                workerId,
                claimTime,
                leaseDuration);
        if (claim == null) {
            return SupportReviewDispatchResult.NOT_CLAIMED;
        }

        SupportReviewReceipt receipt;
        try {
            receipt = remoteGateway.submit(claim.getReviewRequest());
        } catch (SupportReviewPortException exception) {
            return finalizeFailure(claim, exception.getErrorKey());
        } catch (RuntimeException exception) {
            return finalizeFailure(
                    claim,
                    ApiErrorKey.AI_PROVIDER_UNAVAILABLE.value());
        }
        if (receipt == null) {
            return finalizeFailure(
                    claim,
                    ApiErrorKey.AI_RESPONSE_INVALID.value());
        }
        transactionService.finalizeSuccess(claim, receipt, now());
        return SupportReviewDispatchResult.SENT;
    }

    private SupportReviewDispatchResult finalizeFailure(
            SupportReviewClaim claim,
            String errorCode) {
        LocalDateTime failureTime = now();
        return transactionService.finalizeFailure(
                claim,
                failureTime,
                failureTime.plus(retryDelay),
                maxAttempts,
                errorCode);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                StableUnits.ASIA_SHANGHAI);
    }
}
