package com.plagod.ai.provider;

public final class AiProviderException extends RuntimeException {

    private final AiProviderFailure failure;

    private AiProviderException(
            AiProviderFailure failure,
            String safeMessage) {
        super(safeMessage);
        this.failure = failure;
    }

    public static AiProviderException unavailable() {
        return new AiProviderException(
                AiProviderFailure.UNAVAILABLE,
                "AI Provider 当前不可用");
    }

    public static AiProviderException timeout(Throwable cause) {
        return new AiProviderException(
                AiProviderFailure.TIMEOUT,
                "AI Provider 调用超时");
    }

    public static AiProviderException callFailed(Throwable cause) {
        return new AiProviderException(
                AiProviderFailure.CALL_FAILED,
                "AI Provider 调用失败");
    }

    public static AiProviderException responseTooLarge() {
        return new AiProviderException(
                AiProviderFailure.RESPONSE_TOO_LARGE,
                "AI Provider 响应超过大小上限");
    }

    public static AiProviderException responseInvalid() {
        return new AiProviderException(
                AiProviderFailure.RESPONSE_INVALID,
                "AI Provider 响应不符合固定 Schema");
    }

    public AiProviderFailure getFailure() {
        return failure;
    }
}
