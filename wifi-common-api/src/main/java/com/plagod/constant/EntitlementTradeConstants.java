package com.plagod.constant;

public final class EntitlementTradeConstants {

    private EntitlementTradeConstants() {
    }

    public static final String MODE_DURATION = "DURATION";
    public static final String MODE_SUBSCRIPTION = "SUBSCRIPTION";

    public static final String ORDER_PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String ORDER_PAID = "PAID";
    public static final String ORDER_FULFILLED = "FULFILLED";
    public static final String ORDER_CANCELLED = "CANCELLED";
    public static final String ORDER_CLOSED = "CLOSED";
    public static final String ORDER_REFUNDING = "REFUNDING";
    public static final String ORDER_PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
    public static final String ORDER_REFUNDED = "REFUNDED";

    public static final String PAYMENT_CREATED = "CREATED";
    public static final String PAYMENT_SUCCEEDED = "SUCCEEDED";
    public static final String PAYMENT_FAILED = "FAILED";
    public static final String PAYMENT_CLOSED = "CLOSED";
    public static final String PAYMENT_PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
    public static final String PAYMENT_REFUNDED = "REFUNDED";

    public static final String REFUND_REQUESTED = "REQUESTED";
    public static final String REFUND_REJECTED = "REJECTED";
    public static final String REFUND_PROCESSING = "PROCESSING";
    public static final String REFUND_SUCCEEDED = "SUCCEEDED";
    public static final String REFUND_FAILED = "FAILED";

    public static final String BUSINESS_ORDER = "ORDER";
    public static final String BUSINESS_PAYMENT = "PAYMENT";
    public static final String BUSINESS_REFUND = "REFUND";

    public static final String OPERATOR_USER = "USER";
    public static final String OPERATOR_ADMIN = "ADMIN";
    public static final String OPERATOR_CHANNEL = "CHANNEL";
    public static final String OPERATOR_SYSTEM = "SYSTEM";

    public static final String CHANNEL_LOCAL_DEMO = "LOCAL_DEMO";

    public static final int PURCHASE_USABLE = 1;
    public static final int PURCHASE_EXHAUSTED = 2;
    public static final int PURCHASE_REFUNDED = 3;
    // 已申请退款并冻结剩余时长，等待审核或渠道结果。
    public static final int PURCHASE_REFUND_RESERVED = 4;
}