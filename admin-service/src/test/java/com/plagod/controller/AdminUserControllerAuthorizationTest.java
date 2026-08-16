package com.plagod.controller;

import com.plagod.exception.ApiStatusException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminUserControllerAuthorizationTest {

    private final AdminUserController controller = new AdminUserController();

    @Test
    void shouldAllowPlatformSuperAdmin() {
        assertDoesNotThrow(() -> controller.requirePlatformSuperAdmin(0));
    }

    @Test
    void shouldRejectTenantAdmin() {
        assertForbidden(1);
    }

    @Test
    void shouldRejectMissingRole() {
        assertForbidden(null);
    }

    private void assertForbidden(Integer role) {
        ApiStatusException exception = assertThrows(
                ApiStatusException.class,
                () -> controller.requirePlatformSuperAdmin(role));

        assertEquals(403, exception.getHttpStatus());
        assertEquals(403, exception.getCode());
    }
}
