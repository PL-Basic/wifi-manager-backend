package com.plagod.service;

import com.plagod.entity.MarketplaceOrder;

public final class MarketplaceOrderReplayResult {

    private final MarketplaceOrder order;
    private final boolean duplicate;

    public MarketplaceOrderReplayResult(MarketplaceOrder order, boolean duplicate) {
        this.order = order;
        this.duplicate = duplicate;
    }

    public MarketplaceOrder getOrder() {
        return order;
    }

    public boolean isDuplicate() {
        return duplicate;
    }
}
