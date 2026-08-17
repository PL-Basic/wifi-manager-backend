package com.plagod.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.dto.device.PortalAuthorizeDTO;
import com.plagod.dto.tenant.TenantCreateRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRequestIdContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void tenantCreateRequiresSafeClientRequestId() {
        TenantCreateRequest request = validTenantCreate();

        assertClientRequestIdContract(request);
    }

    @Test
    void portalAuthorizeRequiresSafeClientRequestId() {
        PortalAuthorizeDTO request = validPortalAuthorize();

        assertClientRequestIdContract(request);
    }

    @Test
    void clientRequestIdRoundTripsAsAdditiveJsonField() throws Exception {
        TenantCreateRequest tenant = validTenantCreate();
        PortalAuthorizeDTO portal = validPortalAuthorize();

        JsonNode tenantJson = OBJECT_MAPPER.valueToTree(tenant);
        JsonNode portalJson = OBJECT_MAPPER.valueToTree(portal);
        TenantCreateRequest restoredTenant = OBJECT_MAPPER.treeToValue(
                tenantJson,
                TenantCreateRequest.class);
        PortalAuthorizeDTO restoredPortal = OBJECT_MAPPER.treeToValue(
                portalJson,
                PortalAuthorizeDTO.class);

        assertEquals("tenant:create-1", tenantJson.path(
                "clientRequestId").asText());
        assertEquals("portal.authorize_1", portalJson.path(
                "clientRequestId").asText());
        assertEquals(
                tenant.getClientRequestId(),
                restoredTenant.getClientRequestId());
        assertEquals(
                portal.getClientRequestId(),
                restoredPortal.getClientRequestId());
    }

    @Test
    void legacyJsonStillDeserializesButFailsRequiredValidation()
            throws Exception {
        TenantCreateRequest tenant = OBJECT_MAPPER.readValue(
                "{\"tenantCode\":\"demo-tenant\","
                        + "\"name\":\"Demo Tenant\","
                        + "\"timezone\":\"Asia/Shanghai\"}",
                TenantCreateRequest.class);
        PortalAuthorizeDTO portal = OBJECT_MAPPER.readValue(
                "{\"deviceCode\":\"esp32-demo\","
                        + "\"mac\":\"AA:BB:CC:DD:EE:FF\","
                        + "\"ip\":\"192.0.2.10\"}",
                PortalAuthorizeDTO.class);

        assertNull(tenant.getClientRequestId());
        assertNull(portal.getClientRequestId());
        assertTrue(hasClientRequestIdViolation(validator.validate(tenant)));
        assertTrue(hasClientRequestIdViolation(validator.validate(portal)));
    }

    private static <T> void assertClientRequestIdContract(T request) {
        setClientRequestId(request, "a");
        assertFalse(hasClientRequestIdViolation(validator.validate(request)));

        setClientRequestId(
                request,
                "A2345678901234567890123456789012345678901234567890123456789012:4");
        assertFalse(hasClientRequestIdViolation(validator.validate(request)));

        for (String invalid : new String[]{
                null,
                "",
                " ",
                "_starts-with-symbol",
                "contains space",
                "contains/slash",
                "A2345678901234567890123456789012345678901234567890123456789012345"
        }) {
            setClientRequestId(request, invalid);
            assertTrue(
                    hasClientRequestIdViolation(validator.validate(request)),
                    String.valueOf(invalid));
        }
    }

    private static void setClientRequestId(Object value, String requestId) {
        if (value instanceof TenantCreateRequest) {
            ((TenantCreateRequest) value).setClientRequestId(requestId);
            return;
        }
        ((PortalAuthorizeDTO) value).setClientRequestId(requestId);
    }

    private static boolean hasClientRequestIdViolation(
            Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().anyMatch(violation ->
                "clientRequestId".equals(
                        violation.getPropertyPath().toString()));
    }

    private static TenantCreateRequest validTenantCreate() {
        TenantCreateRequest request = new TenantCreateRequest();
        request.setClientRequestId("tenant:create-1");
        request.setTenantCode("demo-tenant");
        request.setName("Demo Tenant");
        request.setTimezone("Asia/Shanghai");
        return request;
    }

    private static PortalAuthorizeDTO validPortalAuthorize() {
        PortalAuthorizeDTO request = new PortalAuthorizeDTO();
        request.setClientRequestId("portal.authorize_1");
        request.setDeviceCode("esp32-demo");
        request.setMac("AA:BB:CC:DD:EE:FF");
        request.setIp("192.0.2.10");
        return request;
    }
}
