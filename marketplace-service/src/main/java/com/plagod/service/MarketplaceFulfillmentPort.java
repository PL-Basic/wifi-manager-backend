package com.plagod.service;

@FunctionalInterface
public interface MarketplaceFulfillmentPort {

    MarketplaceFulfillmentResult fulfill(MarketplaceFulfillmentRequest request)
            throws Exception;
}
