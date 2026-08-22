package com.plagod.service;

import com.plagod.dto.OAuthStateContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

@Service
public class OAuthStateClaimTransactionBoundary {

    private final OAuthStateTransactionService stateService;
    private final TransactionTemplate stateTransactionTemplate;

    public OAuthStateClaimTransactionBoundary(
            OAuthStateTransactionService stateService,
            PlatformTransactionManager transactionManager) {
        this.stateService = stateService;
        this.stateTransactionTemplate =
                new TransactionTemplate(transactionManager);
        this.stateTransactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public OAuthStateContext claim(
            String provider,
            String rawState,
            String authorizationCode) {
        return Objects.requireNonNull(stateTransactionTemplate.execute(
                status -> stateService.claim(
                        provider,
                        rawState,
                        authorizationCode)));
    }

    public void complete(
            OAuthStateContext context,
            String resultStatus,
            Long resultUserId,
            String message) {
        stateTransactionTemplate.executeWithoutResult(status ->
                stateService.complete(
                        context,
                        resultStatus,
                        resultUserId,
                        message));
    }

    public void fail(OAuthStateContext context, String message) {
        stateTransactionTemplate.executeWithoutResult(status ->
                stateService.fail(context, message));
    }
}
