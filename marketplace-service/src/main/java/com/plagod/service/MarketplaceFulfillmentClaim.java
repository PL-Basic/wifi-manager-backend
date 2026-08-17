package com.plagod.service;

import com.plagod.entity.MarketplaceFulfillment;

public final class MarketplaceFulfillmentClaim {

    private final Long fulfillmentId;
    private final Long tenantId;
    private final Long orderId;
    private final Long orderItemId;
    private final String eventKey;
    private final String fulfillmentType;
    private final String targetBusinessKey;
    private final String workerId;
    private final int attemptCount;

    private MarketplaceFulfillmentClaim(
            MarketplaceFulfillment fulfillment,
            String workerId) {
        this.fulfillmentId = fulfillment.getFulfillmentId();
        this.tenantId = fulfillment.getTenantId();
        this.orderId = fulfillment.getOrderId();
        this.orderItemId = fulfillment.getOrderItemId();
        this.eventKey = fulfillment.getEventKey();
        this.fulfillmentType = fulfillment.getFulfillmentType();
        this.targetBusinessKey = fulfillment.getTargetBusinessKey();
        this.workerId = workerId;
        this.attemptCount = fulfillment.getAttemptCount();
    }

    public static MarketplaceFulfillmentClaim from(
            MarketplaceFulfillment fulfillment,
            String workerId) {
        return new MarketplaceFulfillmentClaim(fulfillment, workerId);
    }

    public Long getFulfillmentId() {
        return fulfillmentId;
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

    public String getEventKey() {
        return eventKey;
    }

    public String getFulfillmentType() {
        return fulfillmentType;
    }

    public String getTargetBusinessKey() {
        return targetBusinessKey;
    }

    public String getWorkerId() {
        return workerId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
