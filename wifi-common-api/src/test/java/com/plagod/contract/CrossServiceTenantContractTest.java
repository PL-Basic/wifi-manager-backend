package com.plagod.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.device.TrafficEvaluationRequest;
import com.plagod.vo.device.LocationSessionContextVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CrossServiceTenantContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void trafficEvaluationCarriesTenantIdAcrossJsonBoundary()
            throws Exception {
        TrafficEvaluationRequest request = new TrafficEvaluationRequest();
        request.setEventId("traffic-event-1");
        request.setTenantId(17L);

        JsonNode json = OBJECT_MAPPER.valueToTree(request);
        TrafficEvaluationRequest restored = OBJECT_MAPPER.treeToValue(
                json,
                TrafficEvaluationRequest.class);

        assertEquals(17L, json.path("tenantId").asLong());
        assertEquals(Long.valueOf(17L), restored.getTenantId());
        assertEquals("traffic-event-1", restored.getEventId());
    }

    @Test
    void locationSessionContextCarriesTenantIdAcrossJsonBoundary()
            throws Exception {
        LocationSessionContextVO context = new LocationSessionContextVO();
        context.setSessionId(31L);
        context.setTenantId(17L);

        JsonNode json = OBJECT_MAPPER.valueToTree(context);
        LocationSessionContextVO restored = OBJECT_MAPPER.treeToValue(
                json,
                LocationSessionContextVO.class);

        assertEquals(17L, json.path("tenantId").asLong());
        assertEquals(Long.valueOf(17L), restored.getTenantId());
        assertEquals(Long.valueOf(31L), restored.getSessionId());
    }

    @Test
    void legacyPayloadWithoutTenantIdRemainsDeserializable()
            throws Exception {
        TrafficEvaluationRequest traffic = OBJECT_MAPPER.readValue(
                "{\"eventId\":\"traffic-event-1\"}",
                TrafficEvaluationRequest.class);
        LocationSessionContextVO location = OBJECT_MAPPER.readValue(
                "{\"sessionId\":31}",
                LocationSessionContextVO.class);

        assertNull(traffic.getTenantId());
        assertNull(location.getTenantId());
    }
}
