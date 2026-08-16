package com.plagod.ai.observability;

public enum AiModerationMetricOutcome {

    APPROVED("SUCCESS", "approve"),
    REJECTED("SUCCESS", "reject"),
    PROVIDER_MANUAL("MANUAL", "provider-manual"),
    LOW_CONFIDENCE("MANUAL", "low-confidence"),
    CONTENT_HASH_MISMATCH("MANUAL", "content-hash"),
    PROVIDER_UNAVAILABLE("MANUAL", "provider-unavailable"),
    RESPONSE_INVALID("MANUAL", "response-invalid");

    private final String result;
    private final String type;

    AiModerationMetricOutcome(String result, String type) {
        this.result = result;
        this.type = type;
    }

    public String getResult() {
        return result;
    }

    public String getType() {
        return type;
    }
}
