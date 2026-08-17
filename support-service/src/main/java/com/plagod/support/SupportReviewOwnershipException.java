package com.plagod.support;

public class SupportReviewOwnershipException extends IllegalStateException {

    public SupportReviewOwnershipException() {
        super("Support review Outbox 不再由当前 worker 持有");
    }
}
