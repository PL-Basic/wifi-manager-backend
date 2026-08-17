package com.plagod.service;

public final class MarketplaceFulfillmentResult {

    private final String resultReference;

    public MarketplaceFulfillmentResult(String resultReference) {
        this.resultReference = resultReference;
    }

    public String getResultReference() {
        return resultReference;
    }
}
