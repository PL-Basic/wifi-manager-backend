package com.plagod.dto.entitlement;

import lombok.Data;

@Data
public class VerifiedPaymentCallback {

    private String channel;
    private String businessKey;
    private String eventId;
    private String channelTransactionNo;
    private Long paidAmountCents;
    private String payloadHash;
}