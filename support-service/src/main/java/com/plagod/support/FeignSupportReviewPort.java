package com.plagod.support;

import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.support.client.AiReviewTaskClient;
import com.plagod.support.client.AiReviewTaskRequest;
import com.plagod.support.client.AiReviewTaskResponse;
import org.springframework.stereotype.Service;

@Service
public class FeignSupportReviewPort implements SupportReviewPort {

    private final AiReviewTaskClient reviewTaskClient;

    public FeignSupportReviewPort(AiReviewTaskClient reviewTaskClient) {
        this.reviewTaskClient = reviewTaskClient;
    }

    @Override
    public SupportReviewReceipt submit(SupportReviewRequest request) {
        ApiResponse<AiReviewTaskResponse> response;
        try {
            response = reviewTaskClient.createReviewTask(
                    AiReviewTaskRequest.from(request));
        } catch (RuntimeException exception) {
            throw new SupportReviewPortException(
                    ApiErrorKey.AI_PROVIDER_UNAVAILABLE.value());
        }
        if (response == null) {
            throw invalidResponse();
        }
        if (response.getCode() < 200 || response.getCode() >= 300) {
            String errorKey = ApiErrorKey.isValid(response.getErrorKey())
                    ? response.getErrorKey()
                    : ApiErrorKey.AI_PROVIDER_UNAVAILABLE.value();
            throw new SupportReviewPortException(errorKey);
        }
        AiReviewTaskResponse result = response.getData();
        if (result == null
                || result.getReviewTaskId() == null
                || result.getReviewTaskId() <= 0L) {
            throw invalidResponse();
        }
        return new SupportReviewReceipt(result.getReviewTaskId());
    }

    private SupportReviewPortException invalidResponse() {
        return new SupportReviewPortException(
                ApiErrorKey.AI_RESPONSE_INVALID.value());
    }
}
