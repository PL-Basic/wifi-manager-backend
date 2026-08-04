package com.plagod.controller;

import com.plagod.client.TenantServiceClient;
import com.plagod.exception.ApiStatusException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminPlatformTenantControllerTest {

    @Test
    void ordinaryAdminIsRejectedBeforeCallingTenantService() {
        TenantServiceClient client = mock(TenantServiceClient.class);
        AdminPlatformTenantController controller = new AdminPlatformTenantController(client);

        ApiStatusException exception = assertThrows(ApiStatusException.class,
                () -> controller.pageTenants(1, 1, 20, null));

        assertEquals(403, exception.getHttpStatus());
        verifyNoInteractions(client);
    }
}
