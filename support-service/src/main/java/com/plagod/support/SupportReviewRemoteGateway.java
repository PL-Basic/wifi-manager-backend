package com.plagod.support;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SupportReviewRemoteGateway {

    private final SupportReviewPort reviewPort;
    private final TransactionTemplate withoutTransaction;

    public SupportReviewRemoteGateway(
            SupportReviewPort reviewPort,
            PlatformTransactionManager transactionManager) {
        this.reviewPort = reviewPort;
        TransactionTemplate template = new TransactionTemplate(
                transactionManager);
        template.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.withoutTransaction = template;
    }

    public SupportReviewReceipt submit(SupportReviewRequest request) {
        return withoutTransaction.execute(status -> reviewPort.submit(request));
    }
}
