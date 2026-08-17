package com.plagod.service.impl;

import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mqtt.MqttCommandPublisher;
import com.plagod.security.WifiCommandPayloadCrypto;
import com.plagod.service.DeviceCommandDispatchTransactionService;
import com.plagod.service.DeviceWifiConfigLifecycleService;
import com.plagod.service.SessionCommandLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCommandDispatchServiceTest {

    @Mock
    private DeviceCommandRecordMapper commandRecordMapper;
    @Mock
    private MqttCommandPublisher mqttCommandPublisher;
    @Mock
    private DeviceCommandDispatchTransactionService transactionService;
    @Mock
    private SessionCommandLifecycleService sessionCommandLifecycleService;
    @Mock
    private WifiCommandPayloadCrypto wifiCommandPayloadCrypto;
    @Mock
    private DeviceWifiConfigLifecycleService wifiConfigLifecycleService;

    private DeviceCommandDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceCommandDispatchServiceImpl();
        ReflectionTestUtils.setField(
                service, "commandRecordMapper", commandRecordMapper);
        ReflectionTestUtils.setField(
                service, "mqttCommandPublisher", mqttCommandPublisher);
        ReflectionTestUtils.setField(
                service, "dispatchTransactionService", transactionService);
        ReflectionTestUtils.setField(
                service,
                "sessionCommandLifecycleService",
                sessionCommandLifecycleService);
        ReflectionTestUtils.setField(
                service, "wifiCommandPayloadCrypto", wifiCommandPayloadCrypto);
        ReflectionTestUtils.setField(
                service,
                "wifiConfigLifecycleService",
                wifiConfigLifecycleService);
        ReflectionTestUtils.setField(service, "publishMaxAttempts", 3);
        ReflectionTestUtils.setField(
                service, "publishRetryDelaySeconds", 3L);
        ReflectionTestUtils.setField(service, "dispatchLeaseSeconds", 30L);
        ReflectionTestUtils.setField(service, "resultTimeoutSeconds", 15L);
        ReflectionTestUtils.setField(service, "dispatchWorkerId", "worker-a");
    }

    @Test
    void claimCommitsBeforePublishAndFinalizeUsesFifteenSecondWindow() {
        DeviceCommandRecord claim = claim(1);
        when(transactionService.claim(
                eq(10L),
                eq("worker-a"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(3),
                eq(3L))).thenReturn(claim);
        when(transactionService.finalizePublished(
                eq(10L),
                eq("worker-a"),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(true);

        service.dispatchOne(10L);

        InOrder order = inOrder(
                transactionService, mqttCommandPublisher);
        order.verify(transactionService).claim(
                eq(10L),
                eq("worker-a"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(3),
                eq(3L));
        order.verify(mqttCommandPublisher)
                .publish("wifi/device/node-1/cmd/allow", claim.getPayload());
        order.verify(transactionService).finalizePublished(
                eq(10L),
                eq("worker-a"),
                any(LocalDateTime.class),
                any(LocalDateTime.class));

        ArgumentCaptor<LocalDateTime> publishedAt =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> deadline =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionService).finalizePublished(
                eq(10L),
                eq("worker-a"),
                publishedAt.capture(),
                deadline.capture());
        assertEquals(
                15L,
                Duration.between(
                        publishedAt.getValue(), deadline.getValue())
                        .getSeconds());
    }

    @Test
    void retryPublishesTheSameRequestIdAndPayload() {
        DeviceCommandRecord first = claim(1);
        DeviceCommandRecord second = claim(2);
        when(transactionService.claim(
                eq(10L),
                eq("worker-a"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(3),
                eq(3L))).thenReturn(first, second);
        when(transactionService.finalizeFailure(
                eq(10L),
                eq("worker-a"),
                any(LocalDateTime.class),
                eq(3),
                eq(3L))).thenReturn(true);
        when(transactionService.finalizePublished(
                eq(10L),
                eq("worker-a"),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(true);
        doThrow(new IllegalStateException("offline"))
                .doNothing()
                .when(mqttCommandPublisher)
                .publish(first.getTopic(), first.getPayload());

        service.dispatchOne(10L);
        service.dispatchOne(10L);

        verify(mqttCommandPublisher, times(2))
                .publish(first.getTopic(), first.getPayload());
        assertEquals(first.getRequestId(), second.getRequestId());
        assertEquals(first.getPayload(), second.getPayload());
    }

    private DeviceCommandRecord claim(int attempts) {
        DeviceCommandRecord command = new DeviceCommandRecord();
        command.setCommandId(10L);
        command.setTenantId(1L);
        command.setRequestId("mqtt-request-fixed");
        command.setCommandType("ALLOW");
        command.setPurpose("PORTAL_AUTHORIZE");
        command.setTopic("wifi/device/node-1/cmd/allow");
        command.setPayload(
                "{\"requestId\":\"mqtt-request-fixed\",\"type\":\"ALLOW\"}");
        command.setRetryCount(attempts);
        return command;
    }
}
