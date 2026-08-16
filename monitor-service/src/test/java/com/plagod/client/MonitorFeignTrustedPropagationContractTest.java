package com.plagod.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MonitorFeignTrustedPropagationContractTest {

    @Test
    void clientsDoNotDeclareManualIdentityOrInternalTokenHeaders() {
        assertNoRequestHeaders(DeviceLocationSessionClient.class);
        assertNoRequestHeaders(DeviceSignalAnalyticsClient.class);
        assertNoRequestHeaders(DeviceTrafficAnalyticsClient.class);
    }

    private void assertNoRequestHeaders(Class<?> clientType) {
        for (Method method : clientType.getDeclaredMethods()) {
            Annotation[][] parameterAnnotations =
                    method.getParameterAnnotations();
            for (Annotation[] annotations : parameterAnnotations) {
                for (Annotation annotation : annotations) {
                    assertFalse(
                            annotation.annotationType()
                                    == RequestHeader.class,
                            clientType.getSimpleName()
                                    + "#"
                                    + method.getName()
                                    + " 不能手工声明可信 Header");
                }
            }
        }
    }
}
