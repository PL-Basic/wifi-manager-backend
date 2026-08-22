package com.plagod.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StableSupportContractFixtureTest {

    private static final String RESOURCE_ROOT =
            "/contracts/demo-1.4-s0/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void legacyEnvelopeRemainsCompatibleAndExtensionsAreAdditive()
            throws Exception {
        JsonNode contract = read("http-envelope-v1.json");
        JsonNode actualLegacy = OBJECT_MAPPER.valueToTree(
                ApiResponse.fail(
                        409,
                        "当前状态已变化，请刷新后重试"));

        assertEquals("http-envelope-v1", contract.path("version").asText());
        assertEquals(contract.path("legacyError"), actualLegacy);
        assertEquals(
                fieldNames(contract.path("legacyError")),
                new HashSet<>(Arrays.asList("code", "message", "data")));

        JsonNode extended = contract.path("extendedError");
        for (String field : fieldNames(contract.path("legacyError"))) {
            assertEquals(
                    contract.path("legacyError").path(field),
                    extended.path(field));
        }
        assertEquals(
                "RESOURCE_VERSION_CONFLICT",
                extended.path("errorKey").asText());
        assertTrue(
                Pattern.compile(contract.path("requestId").path("pattern").asText())
                        .matcher(extended.path("requestId").asText())
                        .matches());

        Set<String> errorKeys = new HashSet<>();
        for (JsonNode key : contract.path("baseErrorKeys")) {
            assertTrue(key.asText().matches("^[A-Z][A-Z0-9_]*$"));
            assertTrue(errorKeys.add(key.asText()));
        }
        assertEquals(15, errorKeys.size());
    }

    @Test
    void mqttFixtureFreezesProductionTopicsAndTerminalTypes()
            throws Exception {
        JsonNode contract = read("mqtt-protocol-v1.json");
        assertEquals("mqtt-protocol-v1", contract.path("version").asText());
        assertEquals(1, contract.path("qos").asInt());
        assertFalse(contract.path("retained").asBoolean());

        Set<String> commandNames = new HashSet<>();
        Set<String> commandTopics = new HashSet<>();
        for (JsonNode command : contract.path("commands")) {
            assertTrue(commandNames.add(command.path("name").asText()));
            assertTrue(commandTopics.add(command.path("topicSuffix").asText()));
            String requestId = command.path("sample").path("requestId").asText();
            assertFalse(requestId.isEmpty());
            assertTrue(requestId.length() <= 63);
        }
        assertEquals(
                new HashSet<>(Arrays.asList(
                        "ALLOW",
                        "REVOKE_ACCESS",
                        "KICK",
                        "DISCONNECT_MAC",
                        "BLOCK_TRAFFIC",
                        "STAGE_WIFI_CONFIG")),
                commandNames);

        JsonNode commandResult = findByName(
                contract.path("events"),
                "COMMAND_RESULT");
        assertNotNull(commandResult);
        assertEquals(commandNames, textSet(commandResult.path("terminalTypes")));
        JsonNode stageWifi = findByName(
                contract.path("commands"),
                "STAGE_WIFI_CONFIG");
        assertNotNull(stageWifi);
        assertEquals(
                7,
                stageWifi.path("sample")
                        .path("configVersion")
                        .asInt());
    }

    @Test
    void aiSchemaRejectsUnknownFieldsAndFailsClosedToManual()
            throws Exception {
        JsonNode schema = read("ai-moderation-result-v1.schema.json");
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals(
                new HashSet<>(Arrays.asList("APPROVE", "REJECT", "MANUAL")),
                textSet(schema.path("properties").path("decision").path("enum")));
        assertEquals(
                0.0D,
                schema.path("properties").path("confidence").path("minimum").asDouble());
        assertEquals(
                1.0D,
                schema.path("properties").path("confidence").path("maximum").asDouble());
        assertEquals("MANUAL", schema.path("x-wifi-failureFallback").asText());
        assertEquals(
                "REJECT",
                schema.path("x-wifi-unknownFieldPolicy").asText());
    }

    private JsonNode read(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                RESOURCE_ROOT + name)) {
            assertNotNull(input, "missing contract fixture: " + name);
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> fields = new HashSet<>();
        Iterator<String> iterator = object.fieldNames();
        while (iterator.hasNext()) {
            fields.add(iterator.next());
        }
        return fields;
    }

    private Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        for (JsonNode value : array) {
            values.add(value.asText());
        }
        return values;
    }

    private JsonNode findByName(JsonNode array, String name) {
        for (JsonNode value : array) {
            if (name.equals(value.path("name").asText())) {
                return value;
            }
        }
        return null;
    }

}
