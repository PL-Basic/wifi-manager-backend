package com.plagod.service;

import com.plagod.entity.MarketplaceFulfillment;
import com.plagod.entity.MarketplaceOrder;

/**
 * Marketplace 首次订单及其履约 Outbox 的原子写入输入。
 */
public final class MarketplaceOrderCreation {

    private final MarketplaceOrder order;
    private final MarketplaceFulfillment fulfillment;

    public MarketplaceOrderCreation(
            MarketplaceOrder order,
            MarketplaceFulfillment fulfillment) {
        this.order = order;
        this.fulfillment = fulfillment;
    }

    public MarketplaceOrder getOrder() {
        return order;
    }

    public MarketplaceFulfillment getFulfillment() {
        return fulfillment;
    }
}
