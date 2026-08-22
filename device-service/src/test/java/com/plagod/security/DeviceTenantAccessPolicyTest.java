package com.plagod.security;

import com.plagod.controller.DeviceController;
import com.plagod.dto.ApiResponse;
import com.plagod.entity.device.Esp32Node;
import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.request.RequestId;
import com.plagod.service.DeviceCommandService;
import com.plagod.service.impl.DeviceCommandServiceImpl;
import com.plagod.vo.device.DeviceNodeVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTenantAccessPolicyTest {

    private static final Long TENANT_A = 11L;
    private static final Long TENANT_B = 22L;
    private static final Long NODE_ID = 99L;

    @Test
    void tenantAdminLoadsOwnedDeviceWithContextTenant() {
        TrustedRequestContextResolver resolver =
                mock(TrustedRequestContextResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TrustedRequestHeaders.TENANT_ID,
                String.valueOf(TENANT_B));
        when(resolver.resolve(request)).thenReturn(tenantAdminContext());

        DeviceTenantAccessPolicy policy =
                new DeviceTenantAccessPolicy(resolver);
        DeviceCommandService deviceCommandService =
                mock(DeviceCommandService.class);
        DeviceNodeVO expected = new DeviceNodeVO();
        expected.setNodeId(NODE_ID);
        when(deviceCommandService.getDevice(TENANT_A, NODE_ID))
                .thenReturn(expected);

        DeviceController controller = new DeviceController();
        ReflectionTestUtils.setField(
                controller,
                "deviceCommandService",
                deviceCommandService);
        ReflectionTestUtils.setField(
                controller,
                "tenantAccessPolicy",
                policy);

        ApiResponse<DeviceNodeVO> response =
                controller.getDevice(NODE_ID, request);

        assertEquals(NODE_ID, response.getData().getNodeId());
        verify(deviceCommandService).getDevice(TENANT_A, NODE_ID);
    }

    @Test
    void tenantScopedLoadReturnsOwnedDeviceAndHidesOtherTenant() {
        Esp32NodeMapper mapper = mock(Esp32NodeMapper.class);
        DeviceCommandServiceImpl service = new DeviceCommandServiceImpl();
        ReflectionTestUtils.setField(service, "esp32NodeMapper", mapper);

        Esp32Node owned = new Esp32Node();
        owned.setNodeId(NODE_ID);
        owned.setTenantId(TENANT_A);
        when(mapper.selectByNodeIdAndTenantIncludeDeleted(
                TENANT_A,
                NODE_ID)).thenReturn(owned);

        DeviceNodeVO result = service.getDevice(TENANT_A, NODE_ID);
        assertEquals(NODE_ID, result.getNodeId());

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> service.getDevice(TENANT_B, NODE_ID));
        assertEquals(404, exception.getHttpStatus());
        verify(mapper).selectByNodeIdAndTenantIncludeDeleted(
                TENANT_B,
                NODE_ID);
    }

    @Test
    void platformContextCannotAccessTenantDeviceDirectly() {
        TrustedRequestContextResolver resolver =
                mock(TrustedRequestContextResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolve(request)).thenReturn(platformContext());

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> new DeviceTenantAccessPolicy(resolver)
                        .requireAdminTenantId(request));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void pureInternalTokenDoesNotGrantTenantResourceAccess() {
        TrustedRequestContextResolver resolver =
                mock(TrustedRequestContextResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolve(request)).thenReturn(
                TrustedRequestContext.internalService(
                        "device-request-0004"));

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> new DeviceTenantAccessPolicy(resolver)
                        .requireAdminTenantId(request));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void platformTenantUsesExplicitManagedTenant() {
        TrustedRequestContextResolver resolver =
                mock(TrustedRequestContextResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolve(request)).thenReturn(platformTenantContext());

        Long tenantId = new DeviceTenantAccessPolicy(resolver)
                .requireAdminTenantId(request);

        assertEquals(TENANT_A, tenantId);
    }

    @Test
    void ordinaryTenantMemberCannotUseAdminDeviceEndpoint() {
        TrustedRequestContextResolver resolver =
                mock(TrustedRequestContextResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(resolver.resolve(request)).thenReturn(
                tenantContext("MEMBER"));

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> new DeviceTenantAccessPolicy(resolver)
                        .requireAdminTenantId(request));

        assertEquals(403, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("无权管理设备"));
    }

    @Test
    void malformedTrustedInternalUserContextMapsToAuthenticationError() {
        MockHttpServletRequest request = trustedRequest(
                TrustedRequestHeaders.SOURCE_INTERNAL);
        request.addHeader(TrustedRequestHeaders.USER_ID, "7");

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> new DeviceTenantAccessPolicy(
                        new TrustedRequestContextResolver())
                        .requireAdminTenantId(request));

        assertEquals(401, exception.getHttpStatus());
        assertEquals(
                ApiErrorKey.AUTHENTICATION_REQUIRED.value(),
                exception.getErrorKey());
        assertEquals(
                "可信请求上下文字段缺失",
                exception.getMessage());
    }

    @Test
    void malformedTrustedGatewayContextMapsToPermissionError() {
        MockHttpServletRequest request = trustedRequest(
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader(TrustedRequestHeaders.USER_ID, "1");
        request.addHeader(TrustedRequestHeaders.USER_ROLE, "0");
        request.addHeader(
                TrustedRequestHeaders.SESSION_ID,
                "session-device-04");
        request.addHeader(
                TrustedRequestHeaders.TOKEN_ID,
                "token-device-0004");
        request.addHeader(
                TrustedRequestHeaders.CONTEXT_TYPE,
                TrustedContextType.TENANT.name());
        request.addHeader(
                TrustedRequestHeaders.TENANT_ID,
                String.valueOf(TENANT_A));
        request.addHeader(
                TrustedRequestHeaders.TENANT_CODE,
                "tenant-a");
        request.addHeader(
                TrustedRequestHeaders.TENANT_ROLE,
                "TENANT_ADMIN");
        request.addHeader(
                TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                "3");
        request.addHeader(
                TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                "4");

        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> new DeviceTenantAccessPolicy(
                        new TrustedRequestContextResolver())
                        .requireAdminTenantId(request));

        assertEquals(403, exception.getHttpStatus());
        assertEquals(
                ApiErrorKey.PERMISSION_DENIED.value(),
                exception.getErrorKey());
        assertEquals(
                "平台身份不能伪装为租户成员",
                exception.getMessage());
    }

    private MockHttpServletRequest trustedRequest(String source) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                source);
        request.addHeader(
                RequestId.HEADER_NAME,
                "device-request-0005");
        return request;
    }

    private TrustedRequestContext tenantAdminContext() {
        return tenantContext("TENANT_ADMIN");
    }

    private TrustedRequestContext tenantContext(String tenantRole) {
        return TrustedRequestContext.user(
                TrustedSource.INTERNAL_SERVICE,
                7L,
                1,
                "session-device-01",
                "token-device-0001",
                TrustedContextType.TENANT,
                String.valueOf(TENANT_A),
                "tenant-a",
                tenantRole,
                3L,
                4L,
                Collections.emptyList(),
                "device-request-0001");
    }

    private TrustedRequestContext platformContext() {
        return TrustedRequestContext.user(
                TrustedSource.INTERNAL_SERVICE,
                1L,
                0,
                "session-device-02",
                "token-device-0002",
                TrustedContextType.PLATFORM,
                null,
                null,
                null,
                null,
                null,
                Collections.singletonList("TENANT_READ"),
                "device-request-0002");
    }

    private TrustedRequestContext platformTenantContext() {
        return TrustedRequestContext.user(
                TrustedSource.INTERNAL_SERVICE,
                1L,
                0,
                "session-device-03",
                "token-device-0003",
                TrustedContextType.PLATFORM_TENANT,
                String.valueOf(TENANT_A),
                "tenant-a",
                null,
                3L,
                null,
                Collections.singletonList("TENANT_READ"),
                "device-request-0003");
    }
}
