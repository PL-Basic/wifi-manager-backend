package com.plagod.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseCompatibilityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void nullExtensionsKeepLegacyThreeFieldJson() {
        JsonNode json = OBJECT_MAPPER.valueToTree(
                ApiResponse.fail(409, "状态冲突"));

        assertEquals(
                new HashSet<>(Arrays.asList("code", "message", "data")),
                fieldNames(json));
        assertEquals(409, json.path("code").asInt());
        assertTrue(json.path("data").isNull());
    }

    @Test
    void extendedErrorAddsStableMachineFields() {
        String requestId = RequestId.generate();
        JsonNode json = OBJECT_MAPPER.valueToTree(
                ApiResponse.error(
                        409,
                        "状态冲突",
                        null,
                        ApiErrorKey.RESOURCE_VERSION_CONFLICT.value(),
                        requestId));

        assertEquals(
                ApiErrorKey.RESOURCE_VERSION_CONFLICT.value(),
                json.path("errorKey").asText());
        assertEquals(requestId, json.path("requestId").asText());
        assertTrue(RequestId.isValid(requestId));
    }

    @Test
    void baseRegistryAndStatusFactoriesRemainStable() {
        assertEquals(15, ApiErrorKey.values().length);
        assertEquals(
                ApiErrorKey.AUTHENTICATION_REQUIRED.value(),
                ApiStatusException.authenticationRequired("需要登录")
                        .getErrorKey());
        assertEquals(
                ApiErrorKey.RATE_LIMITED.value(),
                ApiStatusException.tooManyRequests("请求频繁", 0)
                        .getErrorKey());
        assertEquals(
                Long.valueOf(1),
                ApiStatusException.tooManyRequests("请求频繁", 0)
                        .getRetryAfterSeconds());
        assertFalse(ApiErrorKey.isValid("resource-not-found"));
    }

    private HashSet<String> fieldNames(JsonNode json) {
        HashSet<String> names = new HashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
