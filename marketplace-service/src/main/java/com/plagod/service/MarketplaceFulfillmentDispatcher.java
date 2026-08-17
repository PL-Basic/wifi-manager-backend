package com.plagod.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class MarketplaceFulfillmentDispatcher {

    private static final String DEPENDENCY_UNAVAILABLE =
            "DEPENDENCY_UNAVAILABLE";
    private static final String DEPENDENCY_PROTOCOL_INVALID =
            "DEPENDENCY_PROTOCOL_INVALID";

    private final MarketplaceFulfillmentTransactionService transactionService;
    private final MarketplaceFulfillmentPort fulfillmentPort;
    private final TransactionOperations withoutTransaction;
    private final int maxAttempts;
    private final long leaseSeconds;
    private final long retryDelaySeconds;

    public MarketplaceFulfillmentDispatcher(
            MarketplaceFulfillmentTransactionService transactionService,
            MarketplaceFulfillmentPort fulfillmentPort,
            PlatformTransactionManager transactionManager,
            @Value("${wifi.marketplace.fulfillment.max-attempts:5}")
            int maxAttempts,
            @Value("${wifi.marketplace.fulfillment.lease-seconds:30}")
            long leaseSeconds,
            @Value("${wifi.marketplace.fulfillment.retry-delay-seconds:10}")
            long retryDelaySeconds) {
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("履约最大尝试次数必须在 1 到 100 之间");
        }
        if (leaseSeconds < 1 || retryDelaySeconds < 1) {
            throw new IllegalArgumentException("履约 lease 和重试间隔必须大于 0");
        }
        this.transactionService = transactionService;
        this.fulfillmentPort = fulfillmentPort;
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.withoutTransaction = transactionTemplate;
        this.maxAttempts = maxAttempts;
        this.leaseSeconds = leaseSeconds;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public MarketplaceFulfillmentDispatchOutcome dispatchOne(
            Long fulfillmentId,
            String workerId) {
        MarketplaceFulfillmentDispatchOutcome outcome =
                withoutTransaction.execute(status ->
                        dispatchWithoutCallerTransaction(
                                fulfillmentId,
                                workerId));
        if (outcome == null) {
            throw new IllegalStateException("履约调度未返回结果");
        }
        return outcome;
    }

    private MarketplaceFulfillmentDispatchOutcome
            dispatchWithoutCallerTransaction(
                    Long fulfillmentId,
                    String workerId) {
        LocalDateTime claimedAt = LocalDateTime.now();
        MarketplaceFulfillmentClaim claim = transactionService.claim(
                fulfillmentId,
                workerId,
                claimedAt,
                claimedAt.plusSeconds(leaseSeconds),
                maxAttempts);
        if (claim == null) {
            return MarketplaceFulfillmentDispatchOutcome.NOT_CLAIMED;
        }

        MarketplaceFulfillmentResult result;
        try {
            result = fulfillmentPort.fulfill(
                    new MarketplaceFulfillmentRequest(claim));
            if (result == null
                    || !isValidResultReference(
                            result.getResultReference())) {
                return completeFailure(
                        claim,
                        DEPENDENCY_PROTOCOL_INVALID);
            }
        } catch (Exception exception) {
            return completeFailure(claim, DEPENDENCY_UNAVAILABLE);
        }

        transactionService.completeSuccess(
                claim,
                result.getResultReference(),
                LocalDateTime.now());
        return MarketplaceFulfillmentDispatchOutcome.SUCCEEDED;
    }

    private boolean isValidResultReference(String resultReference) {
        return StringUtils.hasText(resultReference)
                && resultReference.length() <= 128;
    }

    private MarketplaceFulfillmentDispatchOutcome completeFailure(
            MarketplaceFulfillmentClaim claim,
            String errorKey) {
        LocalDateTime failedAt = LocalDateTime.now();
        return transactionService.completeFailure(
                claim,
                errorKey,
                failedAt,
                failedAt.plusSeconds(retryDelaySeconds),
                maxAttempts);
    }
}
