package com.plagod.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plagod.constant.DeviceCommandStatus;
import com.plagod.constant.DeviceCommandType;
import com.plagod.dto.CommandResultEvent;
import com.plagod.dto.DeviceStatusEvent;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.DeviceWifiConfigRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mapper.DeviceWifiConfigRecordMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.metrics.DeviceMqttMetrics;
import com.plagod.service.DeviceEventService;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import com.plagod.service.SessionCommandLifecycleService;
import com.plagod.service.impl.CommandResultEventServiceImpl;
import com.plagod.service.impl.DeviceWifiConfigLifecycleServiceImpl;
import com.plagod.testkit.JsonFixtureLoader;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MqttNegativeSamplesContractTest {

    private static final String FIXTURE =
            "contracts/demo-1.4-s0/mqtt-protocol-v1.json";
    private static final String DEVICE_CODE = "esp32-gateway-001";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonNode protocol =
            new JsonFixtureLoader(objectMapper).load(FIXTURE, JsonNode.class);

    @Test
    void frozenNegativeSamplesRemainBoundToProductionRejections()
            throws Exception {
        Set<String> boundSamples = new HashSet<>();
        for (JsonNode negativeSample : protocol.path("negativeSamples")) {
            String name = negativeSample.asText();
            assertTrue(boundSamples.add(name), "duplicate negative sample: " + name);
            assertNegativeSample(name);
        }

        assertEquals(new HashSet<>(Arrays.asList(
                "MISSING_REQUIRED_FIELD",
                "WRONG_FIELD_TYPE",
                "OVERLONG_FIELD",
                "UNKNOWN_ENUM",
                "DUPLICATE_REQUEST_ID",
                "STALE_CONFIG_VERSION",
                "TOPIC_PAYLOAD_DEVICE_MISMATCH")), boundSamples);
    }

    private void assertNegativeSample(String name) throws Exception {
        switch (name) {
            case "MISSING_REQUIRED_FIELD":
                assertMissingRequiredFieldRejected();
                return;
            case "WRONG_FIELD_TYPE":
                assertWrongFieldTypeRejected();
                return;
            case "OVERLONG_FIELD":
                assertOverlongFieldRejected();
                return;
            case "UNKNOWN_ENUM":
                assertUnknownEnumRejected();
                return;
            case "DUPLICATE_REQUEST_ID":
                assertDuplicateRequestIdIsIdempotent();
                return;
            case "STALE_CONFIG_VERSION":
                assertStaleConfigVersionRejected();
                return;
            case "TOPIC_PAYLOAD_DEVICE_MISMATCH":
                assertTopicPayloadDeviceMismatchRejected();
                return;
            default:
                throw new AssertionError(
                        "unbound mqtt-protocol-v1 negative sample: " + name);
        }
    }

    private void assertMissingRequiredFieldRejected() {
        CommandResultEvent event = validResult();
        event.setRequestId(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> new CommandResultEventServiceImpl()
                        .handleCommandResult(event));
    }

    private void assertWrongFieldTypeRejected() {
        ObjectNode payload = objectMapper.valueToTree(validResult());
        payload.putObject("success").put("value", true);

        assertThrows(
                JsonProcessingException.class,
                () -> objectMapper.treeToValue(
                        payload, CommandResultEvent.class));
    }

    private void assertOverlongFieldRejected() {
        CommandResultEvent event = validResult();
        int maximumVisibleBytes = protocol.path("requestId")
                .path("maximumVisibleBytes")
                .asInt();
        event.setRequestId(repeat('a', maximumVisibleBytes + 1));

        assertThrows(
                IllegalArgumentException.class,
                () -> new CommandResultEventServiceImpl()
                        .handleCommandResult(event));
    }

    private void assertUnknownEnumRejected() {
        CommandResultEvent event = validResult();
        event.setType("PING");

        assertThrows(
                IllegalArgumentException.class,
                () -> new CommandResultEventServiceImpl()
                        .handleCommandResult(event));
    }

    private void assertDuplicateRequestIdIsIdempotent() {
        Esp32NodeMapper nodeMapper = mock(Esp32NodeMapper.class);
        DeviceCommandRecordMapper commandMapper =
                mock(DeviceCommandRecordMapper.class);
        DeviceWifiConfigLifecycleService wifiLifecycle =
                mock(DeviceWifiConfigLifecycleService.class);
        SessionCommandLifecycleService sessionLifecycle =
                mock(SessionCommandLifecycleService.class);

        Esp32Node node = new Esp32Node();
        node.setTenantId(7L);
        node.setDeviceCode(DEVICE_CODE);

        DeviceCommandRecord command = new DeviceCommandRecord();
        command.setCommandId(9L);
        command.setTenantId(7L);
        command.setDeviceCode(DEVICE_CODE);
        command.setRequestId("req-duplicate-v1");
        command.setCommandType(DeviceCommandType.ALLOW);
        command.setStatus(DeviceCommandStatus.SUCCEEDED);

        when(nodeMapper.selectByDeviceCodeIncludeDeleted(DEVICE_CODE))
                .thenReturn(node);
        when(commandMapper.selectByRequestIdForUpdate(
                7L, "req-duplicate-v1")).thenReturn(command);

        CommandResultEventServiceImpl service =
                new CommandResultEventServiceImpl();
        ReflectionTestUtils.setField(service, "nodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service, "commandRecordMapper", commandMapper);
        ReflectionTestUtils.setField(
                service, "wifiConfigLifecycleService", wifiLifecycle);
        ReflectionTestUtils.setField(
                service, "sessionCommandLifecycleService", sessionLifecycle);

        CommandResultEvent event = validResult();
        event.setRequestId("req-duplicate-v1");
        service.handleCommandResult(event);

        verify(commandMapper, never()).updateById(
                any(DeviceCommandRecord.class));
        verify(commandMapper).clearEncryptedPayload(
                anyLong(), anyLong(), any(LocalDateTime.class));
        verify(wifiLifecycle).handleTerminalCommand(command);
        verifyNoInteractions(sessionLifecycle);
    }

    private void assertStaleConfigVersionRejected() {
        DeviceWifiConfigRecordMapper mapper =
                mock(DeviceWifiConfigRecordMapper.class);
        DeviceWifiConfigLifecycleServiceImpl service =
                new DeviceWifiConfigLifecycleServiceImpl();
        ReflectionTestUtils.setField(
                service, "wifiConfigRecordMapper", mapper);

        Esp32Node node = new Esp32Node();
        node.setTenantId(7L);
        node.setNodeId(9L);
        node.setDeviceCode(DEVICE_CODE);

        DeviceWifiConfigRecord task = new DeviceWifiConfigRecord();
        task.setTenantId(7L);
        task.setNodeId(9L);
        task.setDeviceCode(DEVICE_CODE);
        task.setRequestId("req-wifi-v1");
        task.setConfigVersion(8L);

        when(mapper.selectByRequestIdForUpdate(7L, "req-wifi-v1"))
                .thenReturn(task);

        DeviceStatusEvent event = new DeviceStatusEvent();
        event.setActiveWifiConfigRequestId("req-wifi-v1");
        event.setActiveWifiConfigVersion(7L);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.handleStatusEvent(
                        node, event, LocalDateTime.now()));
    }

    private void assertTopicPayloadDeviceMismatchRejected()
            throws Exception {
        DeviceMqttMetrics metrics = mock(DeviceMqttMetrics.class);
        DeviceEventService deviceEventService =
                mock(DeviceEventService.class);
        MqttEventSubscriber subscriber = new MqttEventSubscriber();
        ReflectionTestUtils.setField(
                subscriber, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(
                subscriber, "mqttMetrics", metrics);
        ReflectionTestUtils.setField(
                subscriber, "deviceEventService", deviceEventService);

        ObjectNode payload = (ObjectNode) findByName(
                protocol.path("events"), "STATUS")
                .path("sample")
                .deepCopy();
        payload.put("deviceCode", "esp32-gateway-other");

        String topic =
                "wifi/device/" + DEVICE_CODE + "/event/status";
        ReflectionTestUtils.invokeMethod(
                subscriber,
                "handleMessage",
                topic,
                new MqttMessage(objectMapper.writeValueAsString(payload)
                        .getBytes(StandardCharsets.UTF_8)));

        verify(metrics).recordRejected(topic);
        verifyNoInteractions(deviceEventService);
    }

    private JsonNode findByName(JsonNode definitions, String name) {
        for (JsonNode definition : definitions) {
            if (name.equals(definition.path("name").asText())) {
                return definition;
            }
        }
        throw new AssertionError("fixture definition missing: " + name);
    }

    private CommandResultEvent validResult() {
        CommandResultEvent event = new CommandResultEvent();
        event.setDeviceCode(DEVICE_CODE);
        event.setRequestId("req-allow-v1");
        event.setType(DeviceCommandType.ALLOW);
        event.setSuccess(true);
        event.setMessage("allowed");
        return event;
    }

    private String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
