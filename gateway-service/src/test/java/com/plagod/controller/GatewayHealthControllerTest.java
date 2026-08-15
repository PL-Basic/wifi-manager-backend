package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GatewayHealthControllerTest {

    @Test
    void browserHealthOnlyReportsGatewayLiveness() {
        ApiResponse<Map<String, Object>> response =
                new GatewayHealthController().health();

        assertEquals(200, response.getCode());
        assertEquals("gateway-service", response.getData().get("service"));
        assertEquals("UP", response.getData().get("status"));
        assertNotNull(response.getData().get("timestamp"));
        assertEquals(3, response.getData().size());
        assertFalse(response.getData().containsKey("routes"));
        assertFalse(response.getData().containsKey("configuration"));
    }
}
