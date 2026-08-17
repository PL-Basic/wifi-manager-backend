package com.plagod.support;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ApiResponse;
import com.plagod.support.client.AiReviewTaskRequest;
import com.plagod.support.client.AiReviewTaskResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiReviewTaskFeignContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestContainsOnlyAiOwnedReferenceContract() throws Exception {
        AiReviewTaskRequest request = AiReviewTaskRequest.from(
                new SupportReviewRequest(
                        "review-support-001",
                        "SUPPORT_SUBMISSION_REVIEW",
                        "SUPPORT_SUBMISSION",
                        41L,
                        11L,
                        2,
                        repeat("0123456789abcdef", 4),
                        "sensitive title",
                        "sensitive content"));

        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsBytes(request));
        Set<String> names = new HashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        assertEquals(
                new HashSet<>(Arrays.asList(
                        "reviewRequestId",
                        "scene",
                        "businessType",
                        "businessId",
                        "tenantId",
                        "contentVersion",
                        "contentHash")),
                names);
        assertFalse(json.has("policyVersionId"));
        assertFalse(json.has("title"));
        assertFalse(json.has("contentText"));
    }

    @Test
    void apiResponseDeserializesAiReviewTaskIdentifier() throws Exception {
        JavaType responseType = objectMapper.getTypeFactory()
                .constructParametricType(
                        ApiResponse.class,
                        AiReviewTaskResponse.class);
        ApiResponse<AiReviewTaskResponse> response =
                objectMapper.readValue(
                        "{\"code\":200,\"message\":\"操作成功\","
                                + "\"data\":{\"reviewTaskId\":501}}",
                        responseType);

        assertEquals(200, response.getCode());
        assertEquals(501L, response.getData().getReviewTaskId());
    }

    private String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(
                value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }
}
