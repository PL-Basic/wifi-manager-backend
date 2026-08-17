package com.plagod.ai.task;

import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.service.AiModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

@Service
public class AiProviderTransactionBoundary {

    private final AiModerationService moderationService;
    private final TransactionTemplate withoutTransaction;

    public AiProviderTransactionBoundary(
            AiModerationService moderationService,
            PlatformTransactionManager transactionManager) {
        this.moderationService = Objects.requireNonNull(
                moderationService,
                "moderationService 不能为空");
        Objects.requireNonNull(
                transactionManager,
                "transactionManager 不能为空");
        this.withoutTransaction =
                new TransactionTemplate(transactionManager);
        this.withoutTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    public AiModerationResult review(
            AiModerationRequest request,
            double minimumConfidence) {
        AiModerationResult result = withoutTransaction.execute(
                status -> moderationService.review(
                        request,
                        minimumConfidence));
        if (result == null) {
            throw new IllegalStateException("AI Provider 未返回审核结果");
        }
        return result;
    }
}
