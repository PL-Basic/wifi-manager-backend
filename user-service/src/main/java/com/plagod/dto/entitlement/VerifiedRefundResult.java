package com.plagod.dto.entitlement;

import lombok.Data;

@Data
public class VerifiedRefundResult {

    private String channel;
    private String eventId;
    private String channelRefundNo;
    private Boolean success;
    private String payloadHash;
    private String failureMessage;
    private String refundNo;
}