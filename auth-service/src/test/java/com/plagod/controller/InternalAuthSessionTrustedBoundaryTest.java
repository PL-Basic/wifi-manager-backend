package com.plagod.controller;

import com.plagod.client.TenantContextClient;
import com.plagod.security.TrustedRequestFilter;
import com.plagod.security.TrustedRequestHeaders;
import com.plagod.security.TrustedRequestProperties;
import com.plagod.service.AuthSessionService;
import com.plagod.transaction.TestTransactionManager;
import com.plagod.vo.auth.SessionValidationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAuthSessionTrustedBoundaryTest {

    private static final String GATEWAY_TOKEN =
            "a-valid-gateway-token-value";
    private static final String INTERNAL_TOKEN =
            "a-valid-internal-token-value";

    private AuthSessionService authSessionService;
    private TenantContextClient tenantContextClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authSessionService = mock(AuthSessionService.class);
        tenantContextClient = mock(TenantContextClient.class);

        TrustedRequestProperties properties =
                new TrustedRequestProperties();
        properties.setEnabled(true);
        properties.setGatewayToken(GATEWAY_TOKEN);
        properties.setInternalToken(INTERNAL_TOKEN);
        properties.setInternalTokenRequired(true);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new InternalAuthSessionController(
                                authSessionService),
                        new TenantContextController(
                                tenantContextClient,
                                authSessionService,
                                new TestTransactionManager(),
                                INTERNAL_TOKEN))
                .addFilters(new TrustedRequestFilter(properties))
                .build();
    }

    @Test
    void gatewayTokenCannotCallInternalSessionValidation() throws Exception {
        mockMvc.perform(
                        get("/internal/auth/sessions/session-id/validate")
                                .param("userId", "7")
                                .param("jti", "access-jti")
                                .header(
                                        TrustedRequestHeaders.GATEWAY_TOKEN,
                                        GATEWAY_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorKey")
                        .value("AUTHENTICATION_REQUIRED"));

        verify(authSessionService, never()).validate(
                anyString(),
                anyLong(),
                anyString());
    }

    @Test
    void internalTokenCanCallPurposeBuiltSessionEndpointWithoutUserHeaders()
            throws Exception {
        SessionValidationVO validation = new SessionValidationVO();
        validation.setActive(true);
        validation.setStatus("ACTIVE");
        validation.setSessionId("session-id");
        validation.setUserId("7");
        when(authSessionService.validate(
                "session-id",
                7L,
                "access-jti"))
                .thenReturn(validation);

        mockMvc.perform(
                        get("/internal/auth/sessions/session-id/validate")
                                .param("userId", "7")
                                .param("jti", "access-jti")
                                .header(
                                        TrustedRequestHeaders.INTERNAL_TOKEN,
                                        INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(authSessionService).validate(
                "session-id",
                7L,
                "access-jti");
    }

    @Test
    void internalTokenAloneCannotEnterPlatformTenantContext()
            throws Exception {
        mockMvc.perform(
                        post("/auth/platform-context/tenants/19")
                                .contentType("application/json")
                                .content("{\"reason\":\"support\"}")
                                .header(
                                        TrustedRequestHeaders.INTERNAL_TOKEN,
                                        INTERNAL_TOKEN))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                tenantContextClient,
                authSessionService);
    }
}
