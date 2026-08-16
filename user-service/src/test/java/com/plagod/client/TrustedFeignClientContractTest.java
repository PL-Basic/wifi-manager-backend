package com.plagod.client;

import com.plagod.service.impl.DefaultTenantMembershipOutboxServiceImpl;
import com.plagod.service.impl.UserManageServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TrustedFeignClientContractTest {

    @Test
    void clientsDoNotAcceptCallerSuppliedInternalToken()
            throws Exception {
        Method revokeAll = AuthSessionClient.class.getMethod(
                "revokeAll",
                Long.class,
                String.class);
        Method ensureMembership =
                TenantMembershipClient.class.getMethod(
                        "ensureDefaultMembership",
                        com.plagod.dto.tenant
                                .DefaultTenantMembershipRequest.class);

        assertEquals(2, revokeAll.getParameterCount());
        assertEquals(1, ensureMembership.getParameterCount());
        assertFalse(hasRequestHeaderParameter(revokeAll));
        assertFalse(hasRequestHeaderParameter(ensureMembership));
        assertFalse(hasField(
                UserManageServiceImpl.class,
                "internalToken"));
        assertFalse(hasField(
                DefaultTenantMembershipOutboxServiceImpl.class,
                "internalToken"));
    }

    private boolean hasRequestHeaderParameter(Method method) {
        for (Annotation[] parameter : method.getParameterAnnotations()) {
            for (Annotation annotation : parameter) {
                if (annotation.annotationType()
                        == RequestHeader.class) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasField(
            Class<?> type,
            String name) {
        for (Field field : type.getDeclaredFields()) {
            if (name.equals(field.getName())) {
                return true;
            }
        }
        return false;
    }
}
