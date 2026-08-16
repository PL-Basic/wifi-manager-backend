package com.plagod.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.configuration.MqttProperties;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.DeviceCommandType;
import com.plagod.constant.MqttTopics;
import com.plagod.dto.AllowClientCommand;
import com.plagod.dto.BlockTrafficCommand;
import com.plagod.dto.ClientDisconnectEvent;
import com.plagod.dto.ClientSignalEvent;
import com.plagod.dto.CommandResultEvent;
import com.plagod.dto.DeviceStatusEvent;
import com.plagod.dto.DeviceTrafficEvent;
import com.plagod.dto.DisconnectMacCommand;
import com.plagod.dto.KickCommand;
import com.plagod.dto.RevokeAccessCommand;
import com.plagod.dto.StageWifiConfigCommand;
import com.plagod.service.impl.CommandResultEventServiceImpl;
import com.plagod.testkit.JsonFixtureLoader;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttProtocolContractTest {

    private static final String FIXTURE =
            "contracts/demo-1.4-s0/mqtt-protocol-v1.json";
    private static final String DEVICE_CODE = "esp32-gateway-001";
    private static final Comparator<JsonNode> JSON_VALUE_COMPARATOR =
            (left, right) -> {
                if (left.isNumber() && right.isNumber()) {
                    return left.decimalValue().compareTo(right.decimalValue());
                }
                return left.equals(right) ? 0 : 1;
            };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonNode protocol =
            new JsonFixtureLoader(objectMapper).load(FIXTURE, JsonNode.class);

    @Test
    void frozenSamplesRemainBoundToProductionDtosAndTopics() throws Exception {
        assertEquals("mqtt-protocol-v1", protocol.path("version").asText());

        for (JsonNode command : protocol.path("commands")) {
            String name = command.path("name").asText();
            JsonNode sample = command.path("sample");

            assertEquals(expectedTopic(command), productionCommandTopic(name));
            assertRequiredFields(command, sample);
            assertRequestId(sample.path("requestId").asText());

            Object dto = objectMapper.treeToValue(sample, commandDto(name));
            assertJsonEquivalent(sample, objectMapper.valueToTree(dto));
        }

        for (JsonNode event : protocol.path("events")) {
            String name = event.path("name").asText();
            assertEquals(expectedTopic(event), productionEventTopic(name));

            if ("COMMAND_RESULT".equals(name)) {
                assertRoundTrip(event.path("successSample"), CommandResultEvent.class);
                assertRoundTrip(event.path("failureSample"), CommandResultEvent.class);
            } else {
                assertRoundTrip(event.path("sample"), eventDto(name));
            }
        }
    }

    @Test
    void fixtureLocksRequestIdUnitsAndTerminalSemantics() {
        JsonNode allow = findByName(protocol.path("commands"), "ALLOW").path("sample");
        assertTrue(allow.has("ttlSeconds"));
        assertFalse(allow.has("ttlMillis"));

        JsonNode traffic = findByName(protocol.path("events"), "TRAFFIC").path("sample");
        assertTrue(traffic.has("bytesUp"));
        assertTrue(traffic.has("bytesDown"));
        assertFalse(traffic.has("kilobytesUp"));
        assertFalse(traffic.has("kilobytesDown"));

        JsonNode terminalTypes = findByName(
                protocol.path("events"), "COMMAND_RESULT").path("terminalTypes");
        Set<String> fixtureTypes = new HashSet<>();
        for (JsonNode terminalType : terminalTypes) {
            fixtureTypes.add(terminalType.asText());
        }
        assertEquals(DeviceCommandType.terminalTypes(), fixtureTypes);
        assertFalse(DeviceCommandType.isTerminalType("PING"));
        assertFalse(DeviceCommandType.isTerminalType("GET_STATUS"));

        assertFalse(DeviceCommandStatus.isTerminal(DeviceCommandStatus.PENDING));
        assertFalse(DeviceCommandStatus.isTerminal(DeviceCommandStatus.PUBLISHED));
        assertTrue(DeviceCommandStatus.isTerminal(DeviceCommandStatus.SUCCEEDED));
        assertTrue(DeviceCommandStatus.isTerminal(DeviceCommandStatus.EXECUTION_FAILED));
        assertTrue(DeviceCommandStatus.isTerminal(DeviceCommandStatus.PUBLISH_FAILED));
        assertTrue(DeviceCommandStatus.isTerminal(DeviceCommandStatus.TIMED_OUT));
    }

    @Test
    void fixtureQosAndRetainedRemainBoundToProductionPublisher() {
        MqttProperties properties = new MqttProperties();
        assertEquals(
                protocol.path("qos").asInt(),
                properties.getQos().intValue());
        assertEquals(protocol.path("retained").asBoolean(), properties.isRetained());

        MqttCommandPublisher publisher = new MqttCommandPublisher();
        ReflectionTestUtils.setField(publisher, "mqttProperties", properties);
        MqttMessage message = publisher.createMessage("{}");

        assertEquals(protocol.path("qos").asInt(), message.getQos());
        assertEquals(protocol.path("retained").asBoolean(), message.isRetained());
    }

    @Test
    void commandResultRejectsNonVisibleOrOverlongRequestIdBeforePersistence() {
        CommandResultEventServiceImpl service = new CommandResultEventServiceImpl();

        assertThrows(IllegalArgumentException.class,
                () -> service.handleCommandResult(result(repeat('a', 64))));
        assertThrows(IllegalArgumentException.class,
                () -> service.handleCommandResult(result("request-id-中文-0001")));
    }

    @Test
    void sensitiveWifiCommandDoesNotExposePasswordThroughToString() throws Exception {
        JsonNode sample = findByName(
                protocol.path("commands"), "STAGE_WIFI_CONFIG").path("sample");
        StageWifiConfigCommand command = objectMapper.treeToValue(
                sample, StageWifiConfigCommand.class);

        assertFalse(command.toString().contains(sample.path("password").asText()));
    }

    private void assertRequiredFields(JsonNode definition, JsonNode sample) {
        for (JsonNode required : definition.path("required")) {
            assertTrue(sample.has(required.asText()), required.asText());
        }
    }

    private void assertRequestId(String requestId) {
        assertNotNull(requestId);
        assertFalse(requestId.isEmpty());
        assertTrue(requestId.getBytes(StandardCharsets.US_ASCII).length <=
                protocol.path("requestId").path("maximumVisibleBytes").asInt());
        for (int index = 0; index < requestId.length(); index++) {
            assertTrue(requestId.charAt(index) >= 0x21 && requestId.charAt(index) <= 0x7E);
        }
    }

    private void assertRoundTrip(JsonNode sample, Class<?> dtoType) throws Exception {
        Object dto = objectMapper.treeToValue(sample, dtoType);
        assertJsonEquivalent(sample, objectMapper.valueToTree(dto));
    }

    private void assertJsonEquivalent(JsonNode expected, JsonNode actual) {
        assertTrue(expected.equals(JSON_VALUE_COMPARATOR, actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private JsonNode findByName(JsonNode definitions, String name) {
        for (JsonNode definition : definitions) {
            if (name.equals(definition.path("name").asText())) {
                return definition;
            }
        }
        throw new AssertionError("fixture definition missing: " + name);
    }

    private String expectedTopic(JsonNode definition) {
        return protocol.path("topicPrefix").asText()
                .replace("{deviceCode}", DEVICE_CODE)
                + definition.path("topicSuffix").asText();
    }

    private String productionCommandTopic(String name) {
        switch (name) {
            case DeviceCommandType.ALLOW:
                return MqttTopics.deviceAllow(DEVICE_CODE);
            case DeviceCommandType.REVOKE_ACCESS:
                return MqttTopics.deviceRevokeAccess(DEVICE_CODE);
            case DeviceCommandType.KICK:
                return MqttTopics.deviceKick(DEVICE_CODE);
            case DeviceCommandType.DISCONNECT_MAC:
                return MqttTopics.deviceDisconnectMac(DEVICE_CODE);
            case DeviceCommandType.BLOCK_TRAFFIC:
                return MqttTopics.deviceBlockTraffic(DEVICE_CODE);
            case DeviceCommandType.STAGE_WIFI_CONFIG:
                return MqttTopics.deviceStageWifiConfig(DEVICE_CODE);
            default:
                throw new AssertionError("unknown command: " + name);
        }
    }

    private Class<?> commandDto(String name) {
        switch (name) {
            case DeviceCommandType.ALLOW:
                return AllowClientCommand.class;
            case DeviceCommandType.REVOKE_ACCESS:
                return RevokeAccessCommand.class;
            case DeviceCommandType.KICK:
                return KickCommand.class;
            case DeviceCommandType.DISCONNECT_MAC:
                return DisconnectMacCommand.class;
            case DeviceCommandType.BLOCK_TRAFFIC:
                return BlockTrafficCommand.class;
            case DeviceCommandType.STAGE_WIFI_CONFIG:
                return StageWifiConfigCommand.class;
            default:
                throw new AssertionError("unknown command: " + name);
        }
    }

    private String productionEventTopic(String name) {
        switch (name) {
            case "STATUS":
                return String.format(MqttTopics.DEVICE_STATUS, DEVICE_CODE);
            case "TRAFFIC":
                return String.format(MqttTopics.DEVICE_TRAFFIC, DEVICE_CODE);
            case "CLIENT_SIGNAL":
                return MqttTopics.deviceClientSignal(DEVICE_CODE);
            case "CLIENT_DISCONNECT":
                return MqttTopics.deviceClientDisconnect(DEVICE_CODE);
            case "COMMAND_RESULT":
                return MqttTopics.deviceCommandResult(DEVICE_CODE);
            default:
                throw new AssertionError("unknown event: " + name);
        }
    }

    private Class<?> eventDto(String name) {
        switch (name) {
            case "STATUS":
                return DeviceStatusEvent.class;
            case "TRAFFIC":
                return DeviceTrafficEvent.class;
            case "CLIENT_SIGNAL":
                return ClientSignalEvent.class;
            case "CLIENT_DISCONNECT":
                return ClientDisconnectEvent.class;
            default:
                throw new AssertionError("unknown event: " + name);
        }
    }

    private CommandResultEvent result(String requestId) {
        CommandResultEvent event = new CommandResultEvent();
        event.setDeviceCode(DEVICE_CODE);
        event.setRequestId(requestId);
        event.setType(DeviceCommandType.ALLOW);
        event.setSuccess(true);
        return event;
    }

    private String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
