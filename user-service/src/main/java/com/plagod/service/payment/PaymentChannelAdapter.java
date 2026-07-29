package com.plagod.service.payment;

import com.plagod.entity.entitlement.PaymentRecord;

public interface PaymentChannelAdapter {

    String channel();

    PaymentChannelAction initiate(PaymentRecord payment);

    final class PaymentChannelAction {

        private final String type;
        private final String value;

        public PaymentChannelAction(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }
    }
}