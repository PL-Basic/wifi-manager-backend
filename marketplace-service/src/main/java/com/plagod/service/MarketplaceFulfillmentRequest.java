package com.plagod.service;

public final class MarketplaceFulfillmentRequest {

    private final Long tenantId;
    private final Long orderId;
    private final Long orderItemId;
    private final String eventId;
    private final String fulfillmentType;
    private final String targetBusinessKey;

    public MarketplaceFulfillmentRequest(MarketplaceFulfillmentClaim claim) {
        this.tenantId = claim.getTenantId();
        this.orderId = claim.getOrderId();
        this.orderItemId = claim.getOrderItemId();
        this.eventId = claim.getEventKey();
        this.fulfillmentType = claim.getFulfillmentType();
        this.targetBusinessKey = claim.getTargetBusinessKey();
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getFulfillmentType() {
        return fulfillmentType;
    }

    public String getTargetBusinessKey() {
        return targetBusinessKey;
    }
}
