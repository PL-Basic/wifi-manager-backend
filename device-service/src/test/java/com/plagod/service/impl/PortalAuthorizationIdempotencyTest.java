package com.plagod.service.impl;

import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.SessionRecord;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.DeviceUserRemoteGateway;
import com.plagod.service.PortalAuthorizationFingerprint;
import com.plagod.vo.device.SessionRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalAuthorizationIdempotencyTest {

    private static final Long TENANT_ID = 11L;
    private static final Long USER_ID = 22L;

    @Mock
    private DeviceCommandRecordMapper commandRecordMapper;

    @Mock
    private SessionRecordMapper sessionRecordMapper;

    @Mock
    private DeviceUserRemoteGateway userRemoteGateway;

    private PortalSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PortalSessionServiceImpl();
        ReflectionTestUtils.setField(
                service, "deviceCommandRecordMapper", commandRecordMapper);
        ReflectionTestUtils.setField(
                service, "sessionRecordMapper", sessionRecordMapper);
        ReflectionTestUtils.setField(
                service, "userRemoteGateway", userRemoteGateway);
    }

    @Test
    void sameKeyAndFingerprintReplaysStoredSessionWithoutRemoteCall() {
        PortalAuthorizeDTO request = request("portal-request-001");
        String fingerprint =
                PortalAuthorizationFingerprint.calculate(
                        TENANT_ID, USER_ID, request);

        DeviceCommandRecord receipt = new DeviceCommandRecord();
        receipt.setSessionId(33L);
        receipt.setRequestFingerprint(fingerprint);
        when(commandRecordMapper.selectPortalAuthorizationReceipt(
                TENANT_ID, USER_ID, request.getClientRequestId()))
                .thenReturn(receipt);

        SessionRecord session = new SessionRecord();
        session.setSessionId(33L);
        session.setTenantId(TENANT_ID);
        session.setUserId(USER_ID);
        session.setStatus(1);
        when(sessionRecordMapper.selectOwnedById(TENANT_ID, USER_ID, 33L))
                .thenReturn(session);

        SessionRecordVO result = service.authorize(TENANT_ID, request, USER_ID);

        assertEquals(33L, result.getSessionId());
        verifyNoInteractions(userRemoteGateway);
        verify(sessionRecordMapper, never())
                .selectByAuthorizeRequestForUpdate(
                        TENANT_ID, USER_ID, request.getClientRequestId());
    }

    @Test
    void sameKeyWithDifferentFingerprintReturnsFixedConflict() {
        PortalAuthorizeDTO request = request("portal-request-002");
        DeviceCommandRecord receipt = new DeviceCommandRecord();
        receipt.setSessionId(44L);
        receipt.setRequestFingerprint(repeat('a', 64));
        when(commandRecordMapper.selectPortalAuthorizationReceipt(
                TENANT_ID, USER_ID, request.getClientRequestId()))
                .thenReturn(receipt);

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.authorize(TENANT_ID, request, USER_ID));

        assertEquals(409, exception.getHttpStatus());
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", exception.getErrorKey());
        verifyNoInteractions(userRemoteGateway, sessionRecordMapper);
    }

    @Test
    void missingClientRequestIdIsRejectedWithoutFallback() {
        PortalAuthorizeDTO request = request(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.authorize(TENANT_ID, request, USER_ID));

        assertEquals("clientRequestId 不能为空", exception.getMessage());
        verifyNoInteractions(
                commandRecordMapper, sessionRecordMapper, userRemoteGateway);
    }

    @Test
    void fingerprintNormalizesEquivalentInputAndDetectsBusinessChange() {
        PortalAuthorizeDTO left = request("portal-request-003");
        left.setMac("aa:bb:cc:dd:ee:ff");
        left.setDeviceInfo(" browser ");

        PortalAuthorizeDTO equivalent = request("another-key");
        equivalent.setDeviceCode(" node-1 ");
        equivalent.setMac("AA:BB:CC:DD:EE:FF");
        equivalent.setIp(" 192.168.4.2 ");
        equivalent.setDeviceInfo("browser");

        PortalAuthorizeDTO changed = request("portal-request-003");
        changed.setForceReplaceOldest(true);

        assertEquals(
                PortalAuthorizationFingerprint.calculate(
                        TENANT_ID, USER_ID, left),
                PortalAuthorizationFingerprint.calculate(
                        TENANT_ID, USER_ID, equivalent));
        assertNotEquals(
                PortalAuthorizationFingerprint.calculate(
                        TENANT_ID, USER_ID, left),
                PortalAuthorizationFingerprint.calculate(
                        TENANT_ID, USER_ID, changed));
    }

    @Test
    void portalAllowRejectsMissingReceiptMetadata() {
        DeviceCommandServiceImpl commandService =
                new DeviceCommandServiceImpl();

        IllegalArgumentException missingActor = assertThrows(
                IllegalArgumentException.class,
                () -> commandService.allowClient(
                        1L,
                        "node-1",
                        "AA:BB:CC:DD:EE:FF",
                        33L,
                        20,
                        null,
                        "portal-request-004",
                        repeat('a', 64)));
        assertEquals(
                "Portal ALLOW 命令缺少 actorUserId",
                missingActor.getMessage());

        IllegalArgumentException missingClientRequestId = assertThrows(
                IllegalArgumentException.class,
                () -> commandService.allowClient(
                        1L,
                        "node-1",
                        "AA:BB:CC:DD:EE:FF",
                        33L,
                        20,
                        USER_ID,
                        null,
                        repeat('a', 64)));
        assertEquals(
                "Portal ALLOW 命令缺少 clientRequestId",
                missingClientRequestId.getMessage());

        IllegalArgumentException missingFingerprint = assertThrows(
                IllegalArgumentException.class,
                () -> commandService.allowClient(
                        1L,
                        "node-1",
                        "AA:BB:CC:DD:EE:FF",
                        33L,
                        20,
                        USER_ID,
                        "portal-request-004",
                        null));
        assertEquals(
                "Portal ALLOW 命令缺少有效 requestFingerprint",
                missingFingerprint.getMessage());
    }

    private PortalAuthorizeDTO request(String clientRequestId) {
        PortalAuthorizeDTO request = new PortalAuthorizeDTO();
        request.setClientRequestId(clientRequestId);
        request.setDeviceCode("node-1");
        request.setMac("AA:BB:CC:DD:EE:FF");
        request.setIp("192.168.4.2");
        request.setDeviceInfo("browser");
        request.setForceReplaceOldest(false);
        return request;
    }

    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
