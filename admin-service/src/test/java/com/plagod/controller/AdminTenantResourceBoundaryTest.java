package com.plagod.controller;

import com.plagod.client.AdminDownstreamException;
import com.plagod.client.DeviceServiceClient;
import com.plagod.configuration.AdminRequestScopeInterceptor;
import com.plagod.configuration.GlobalExceptionHandler;
import com.plagod.dto.ApiResponse;
import com.plagod.exception.ApiErrorKey;
import com.plagod.request.RequestId;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import com.plagod.vo.device.DeviceNodeVO;
import com.plagod.web.ApiErrorResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTenantResourceBoundaryTest {

    private DeviceServiceClient deviceServiceClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deviceServiceClient = mock(DeviceServiceClient.class);
        AdminDeviceController controller =
                new AdminDeviceController();
        ReflectionTestUtils.setField(
                controller,
                "deviceServiceClient",
                deviceServiceClient);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addInterceptors(new AdminRequestScopeInterceptor(
                        new TrustedRequestContextResolver()))
                .setControllerAdvice(new GlobalExceptionHandler(
                        new ApiErrorResponseFactory()))
                .build();
    }

    @Test
    void tenantAReadsOwnResource() throws Exception {
        when(deviceServiceClient.getDevice(101L))
                .thenReturn(ApiResponse.success(
                        new DeviceNodeVO()));

        mockMvc.perform(get("/admin/devices/101")
                        .with(tenantA()))
                .andExpect(status().isOk());

        verify(deviceServiceClient).getDevice(101L);
    }

    @Test
    void tenantAReadingTenantBResourceKeepsNotFound()
            throws Exception {
        when(deviceServiceClient.getDevice(202L))
                .thenThrow(new AdminDownstreamException(
                        404,
                        null));

        mockMvc.perform(get("/admin/devices/202")
                        .with(tenantA()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorKey").value(
                        ApiErrorKey.RESOURCE_NOT_FOUND.value()));

        verify(deviceServiceClient).getDevice(202L);
    }

    @Test
    void platformDirectTenantResourceIsRejectedBeforeFeign()
            throws Exception {
        mockMvc.perform(get("/admin/devices/101")
                        .with(platform()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deviceServiceClient);
    }

    @Test
    void platformTenantCanUseTenantResourceClient()
            throws Exception {
        when(deviceServiceClient.getDevice(101L))
                .thenReturn(ApiResponse.success(
                        new DeviceNodeVO()));

        mockMvc.perform(get("/admin/devices/101")
                        .with(platformTenant()))
                .andExpect(status().isOk());

        verify(deviceServiceClient).getDevice(101L);
    }

    @Test
    void pureInternalTokenCannotGainTenantResourceAccess()
            throws Exception {
        mockMvc.perform(get("/admin/devices/101")
                        .with(internalService()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deviceServiceClient);
    }

    private RequestPostProcessor tenantA() {
        return request -> {
            trustedUser(request, 1, "TENANT");
            request.addHeader(
                    TrustedRequestHeaders.TENANT_ID,
                    "101");
            request.addHeader(
                    TrustedRequestHeaders.TENANT_CODE,
                    "tenant-a");
            request.addHeader(
                    TrustedRequestHeaders.TENANT_ROLE,
                    "TENANT_ADMIN");
            request.addHeader(
                    TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                    "7");
            request.addHeader(
                    TrustedRequestHeaders.MEMBER_CONTEXT_VERSION,
                    "11");
            return request;
        };
    }

    private RequestPostProcessor platform() {
        return request -> {
            trustedUser(request, 0, "PLATFORM");
            return request;
        };
    }

    private RequestPostProcessor platformTenant() {
        return request -> {
            trustedUser(request, 0, "PLATFORM_TENANT");
            request.addHeader(
                    TrustedRequestHeaders.TENANT_ID,
                    "101");
            request.addHeader(
                    TrustedRequestHeaders.TENANT_CODE,
                    "tenant-a");
            request.addHeader(
                    TrustedRequestHeaders.TENANT_CONTEXT_VERSION,
                    "7");
            request.addHeader(
                    TrustedRequestHeaders.PLATFORM_AUTHORITIES,
                    "TENANT_READ");
            return request;
        };
    }

    private RequestPostProcessor internalService() {
        return request -> {
            trustedSource(
                    request,
                    TrustedRequestHeaders.SOURCE_INTERNAL);
            return request;
        };
    }

    private void trustedUser(
            MockHttpServletRequest request,
            int role,
            String contextType) {
        trustedSource(
                request,
                TrustedRequestHeaders.SOURCE_GATEWAY);
        request.addHeader(TrustedRequestHeaders.USER_ID, "9");
        request.addHeader(
                TrustedRequestHeaders.USER_ROLE,
                String.valueOf(role));
        request.addHeader(
                TrustedRequestHeaders.SESSION_ID,
                "session-00000001");
        request.addHeader(
                TrustedRequestHeaders.TOKEN_ID,
                "token-0000000001");
        request.addHeader(
                TrustedRequestHeaders.CONTEXT_TYPE,
                contextType);
    }

    private void trustedSource(
            MockHttpServletRequest request,
            String source) {
        request.setAttribute(
                TrustedRequestHeaders.TRUSTED_SOURCE_ATTRIBUTE,
                source);
        request.addHeader(
                RequestId.HEADER_NAME,
                "request-id-00000001");
    }
}
