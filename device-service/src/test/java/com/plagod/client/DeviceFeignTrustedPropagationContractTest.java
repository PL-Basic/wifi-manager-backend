package com.plagod.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DeviceFeignTrustedPropagationContractTest {

    @Test
    void feignClientsDoNotAcceptCallerSuppliedTrustedHeaders() {
        assertNoRequestHeaders(UserEntitlementClient.class);
        assertNoRequestHeaders(UserPolicyClient.class);
        assertNoRequestHeaders(MonitorServiceClient.class);
    }

    private void assertNoRequestHeaders(Class<?> clientType) {
        for (Method method : clientType.getDeclaredMethods()) {
            for (Annotation[] parameterAnnotations :
                    method.getParameterAnnotations()) {
                for (Annotation annotation : parameterAnnotations) {
                    assertFalse(
                            annotation instanceof RequestHeader,
                            clientType.getSimpleName()
                                    + "#"
                                    + method.getName()
                                    + " 不能接受调用方手工可信 Header");
                }
            }
        }
    }
}
